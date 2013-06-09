function viterbi_track_video(videoname,model_name,method,lk_ahead)
% Method - 0: standard optical flow
%          1: KLT tracker
% Finds the best box from a particular sequence for each frame using viterbi on
% detector output with higher false positives
% TODO: forward and backward optical-flow/KLT (will require renaming save files)

    dirname = [getenv('HOME') '/video-datasets/C-D1a/SINGLE_VERB/'];
    display(videoname);
    dir_frames = dir([dirname videoname '/0*']);
    load(sprintf('~/video-datasets/C-D1a/voc4-models/%s.mat',model_name));
    addpath('~/darpa-collaboration/optical-flow/bilgic')

    switch method
      case 0
	get_opticalflow(dirname, videoname);
      case 1
	if (exist([dirname, videoname, '/0001/klt-current.txt']) && exist([dirname, videoname, '/0001/klt-next.txt']))
	    disp('Using precomputed KLT files..');
	else
	    disp('Precomputing KLT files..');
	    system(sprintf('~/darpa-collaboration/pedro/tracking/preprocess_klt.sh %s',videoname));
	end
	% getshortestcostpath.m uses standard optical flow regardless
	% TODO: fix this dependence
	get_opticalflow(dirname, videoname);
      otherwise
	disp('Method not handled');
    end

    for frame=1:length(dir_frames)
	sprintf('%s%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name);
	[t,l,b,r,f,c] = textread(sprintf('%s%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name),'%f%f%f%f%f%f');
	boxes{frame} = [t,l,b,r,f,c];
    end

    fprintf('Box prediction:\n');
    possible_boxes = predictboxes(dirname, videoname, boxes, method, lk_ahead);
    for i=1:length(dir_frames)
	added_boxes{i} = setdiff(possible_boxes{i},boxes{i},'rows');
	fid = fopen(sprintf('%s%s/%04d/voc4-%s.predicted_boxes',dirname,videoname,i,model_name), 'w');
	fprintf(fid, '%ld %ld %ld %ld %ld %ld\n', added_boxes{i}');
	fclose(fid);
    end

    % ---- debugging
    unix('mkdir -p -m 775 /tmp/boxes');
    save(sprintf('/tmp/boxes/%s-%s-boxes',videoname,model_name),'possible_boxes');
    unix(sprintf('chmod 664 /tmp/boxes/%s-%s-boxes.mat',videoname,model_name));
    load(sprintf('/tmp/boxes/%s-%s-boxes',videoname,model_name));

    conf_thresh = boxes_conf_thresh(possible_boxes);
    % hardcoded offset to allow full-video tracking
    viterbi_thresh = min((model.thresh - 0.4),conf_thresh)

    fprintf('Viterbi Tracking:\n');
    [path, start, finish] = getshortestcostpath(dirname,videoname,possible_boxes,viterbi_thresh);
    [tracked_boxes,smoothed_boxes] = extract_path(path,finish);
    previous_tracked_boxes = tracked_boxes;

    fprintf('Write tracked boxes 1:\n');
    write_boxes_to_disk(tracked_boxes,smoothed_boxes,dirname,videoname,model_name,1);
    new_boxes = suppress_tracked_boxes(tracked_boxes,possible_boxes,viterbi_thresh);

    instance_num = 2;
    while (instance_num < 4) % sanity check
	[path, start, finish] = getshortestcostpath(dirname,videoname,new_boxes,viterbi_thresh);
	if (start == -1)
	    break;
	end
	[tracked_boxes,smoothed_boxes] = extract_path(path,finish);
	current_tracked_boxes = tracked_boxes;
	[b,r] = continue_viterbi(previous_tracked_boxes,current_tracked_boxes,viterbi_thresh)
	previous_tracked_boxes = [previous_tracked_boxes; current_tracked_boxes];
	if ~b
	    fprintf('Terminating..!\n');
	    break;
	end
	fprintf('Write tracked boxes:\n');
	write_boxes_to_disk(tracked_boxes,smoothed_boxes,dirname,videoname,model_name,instance_num);
	instance_num = instance_num + 1;
	fprintf('%d\n',instance_num);
	new_boxes = suppress_tracked_boxes(tracked_boxes,new_boxes,viterbi_thresh);
    end
    fprintf('Tracking Done.\n');
end

function [b,r] = continue_viterbi(previous_tracked_boxes,current_tracked_boxes,thresh)
    for i = 1:length(current_tracked_boxes)
	for j = 1:length(previous_tracked_boxes)
	    if boxes_close(current_tracked_boxes(i,:),previous_tracked_boxes(j,:))
		current_tracked_boxes(i,6) = thresh;
	    end
	end
    end
    c = current_tracked_boxes(:,6);
    l1 = length(c(c(:)>-intmax));
    l2 = length(c(c(:)>thresh));
    r = l2/l1;
    if (r > 0.3)
	b = 1;
    else
	b = 0;
    end
end

function new_boxes = suppress_tracked_boxes(tracked_boxes,boxes,thresh)
    for i = 1:length(tracked_boxes)
	[t,l,b,r,f,c] = read_single_frame_boxes(boxes,i);
	% rep_c = max(quantile(c,0.25),thresh);
	rep_c = quantile(c,0.25);
	for j = 1:length(c)
	    if boxes_close(tracked_boxes(i,:),[t(j,1) l(j,1) b(j,1) r(j,1)])
		c(j,1) = min(rep_c,c(j,1));
	    end
	end
	new_boxes{i} = [t,l,b,r,f,c];
    end
end

function b = boxes_close(b1,b2)
    box_centre = @(b) [(b(1,3)+b(1,1))/2 (b(1,4)+b(1,2))/2];
    bc2 = box_centre(b2);
    if (bc2(1,1) > min(b1(1,1),b1(1,3)) && bc2(1,1) < max(b1(1,1),b1(1,3)) ...
	&& bc2(1,2) > min(b1(1,2),b1(1,4)) && bc2(1,2) < max(b1(1,2),b1(1,4)))
	b = 1;
    else
	b = 0;
    end
end

function [t,l,b,r,f,c] = read_single_frame_boxes(boxes, index)
    single_box = boxes{index};
    t = single_box(:,1);
    l = single_box(:,2);
    b = single_box(:,3);
    r = single_box(:,4);
    f = single_box(:,5);
    c = single_box(:,6);
end

function conf_thresh = boxes_conf_thresh(boxes)
    for i = 1:length(boxes)
	max_col_boxes = max(boxes{i},[],1);
	if (length(max_col_boxes) == 0)
	    max_conf(i) = intmax; % defaut is 32bit Int
	else
	    max_conf(i) = max_col_boxes(end);
	end
    end
    legal_max_conf = double(max_conf(max_conf(:)<intmax));
    min_conf = min(legal_max_conf);
    max_conf = max(legal_max_conf);
    thresh_range = min_conf:(max_conf-min_conf)/100:max_conf;
    conf_thresh = graythresh(legal_max_conf + abs(min_conf)) - abs(min_conf)
end

function [tracked_boxes,smoothed_boxes] = extract_path(path,finish)
    flip_box = @(b) [b(1,2),b(1,1),b(1,4),b(1,3)];
    flip_point = @(p) [p(1,2),p(1,1)];
    num_f = length(path);

    [d,index] = sort(path{finish}.minDistance_optC2boxC);
    finish_box_index = index(1);
    pre_p = path{finish}.minDistance_path(finish_box_index);

    for i=num_f:-1:1 % num_f-1
	if(path{i}.flag == 0)
	    skip(i) = 1;
	    filter{i} = path{i}.filter(1,:);
	    conf{i} = path{i}.conf(1,:);
	    tracked_boxes(i,:) = [flip_box(path{i}.box(1,:)) filter{i} conf{i}];
	else
	    if (i == finish)
		skip(i) = 0;
		filter{i} = path{i}.filter(finish_box_index,:);
		conf{i} = path{i}.conf(finish_box_index,:);
		tracked_boxes(i,:) = [flip_box(path{i}.box(finish_box_index,:)) filter{i} conf{i}];
		centres(i,:) = flip_point((path{i}.center(finish_box_index,:)).*2); % centres are half-sized
	    else
		skip(i) = 0;
		filter{i} = path{i}.filter(pre_p,:);
		conf{i} = path{i}.conf(pre_p,:);
		tracked_boxes(i,:) = [flip_box(path{i}.box(pre_p,:)) filter{i} conf{i}];
		centres(i,:) = flip_point((path{i}.center(pre_p,:)).*2); % centres are half-sized
		pre_p = path{i}.minDistance_path(pre_p);
	    end
	end
    end

    addpath('~/darpa-collaboration/splines');
    ts = [1:num_f];
    new_ts = ts(skip()==0);
    new_centres = double(centres(skip()==0,:));
    widths = abs(tracked_boxes(:,1)-tracked_boxes(:,3));
    heights = abs(tracked_boxes(:,2)-tracked_boxes(:,4));
    new_widths = double(widths(skip()==0));
    new_heights = double(heights(skip()==0));
    ppx = splinefit(new_ts,new_centres(:,1),10,4);
    ppy = splinefit(new_ts,new_centres(:,2),10,4);
    smx = ppval(ppx,ts);
    smy = ppval(ppy,ts);
    ppw = splinefit(new_ts,new_widths(:),10,4);
    pph = splinefit(new_ts,new_heights(:),10,4);
    smw = ppval(ppw,ts);
    smh = ppval(pph,ts);

    for i=1:num_f
	if (path{i}.flag == 0)
	    smoothed_boxes(i,:) = tracked_boxes(i,:);
	else
	    smoothed_boxes(i,:) = [smx(i)-round(smw(i)/2) smy(i)-round(smh(i)/2) ...
				smx(i)+round(smw(i)/2) smy(i)+round(smh(i)/2) filter{i} conf{i}];
	end
    end
end

function d = write_boxes_to_disk(tracked_boxes,smoothed_boxes,dirname,videoname,model_name,num)
    for i = length(tracked_boxes):-1:1
	dlmwrite(sprintf('%s%s/%04d/voc4-%s-%d.',dirname,videoname,i,model_name,num,...
			 'tracked_box'),tracked_boxes(i,:),'delimiter',' ');
	dlmwrite(sprintf('%s%s/%04d/voc4-%s-%d.',dirname,videoname,i,model_name,num,...
			 'smooth_tracked_box'),smoothed_boxes(i,:),'delimiter',' ');
    end
end
