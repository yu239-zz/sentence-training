function viterbi_track_boxes(dirname,videoname,model_name)

    addpath('~/darpa-collaboration/ideas')
    addpath('~/darpa-collaboration/optical-flow/bilgic')

    [dirname, name, ext] = fileparts(videoname);
    if isempty(dirname)
	dirname = [getenv('HOME') '/video-datasets/C-D1a/SINGLE_VERB/'];
    end
    videoname = [name ext];

    [model_dirname, model_name, model_ext] = fileparts(model_name);
    if isempty(model_ext)
	model_ext = '.mat'
    end
    if isempty(model_dirname)
	model_dirname = [getenv('HOME') '/video-datasets/C-D1a/voc4-models/'];
    end

    display(videoname);
    dir_frames = dir_regexp([dirname videoname],'^[0-9]+$');
    [model_dirname model_name model_ext]
    load([model_dirname '/' model_name model_ext])

    dirlist = dir([dirname '/' videoname '/0001' '/*' model_name '*.boxes']);
    num_models = length(dirlist) %debugging ;
    length(dir_frames) % debugging
    dirlist.name % debugging

    m_empty_num = 0;
    for m = 1:num_models
	model_i = m - m_empty_num;
	[p,n,e] = fileparts(dirlist(m).name);
	for frame = 1:length(dir_frames)
	    fname_1 = sprintf('%s/%s/%04d/%s.boxes',dirname,videoname,frame,n);
	    try
		% tmp_b{1} = dlmread(fname_1,' ');
		% [t,l,b,r,f,c] = read_single_frame_boxes(tmp_b,1);
		[t,l,b,r,f,c] = textread(fname_1,'%f%f%f%f%f%f');
	    catch exception
		fprintf([fname_1 '\n']);
		rethrow(exception);
	    end
	    all_boxes{model_i}{frame} = [t,l,b,r,f,c];
	    if strcmp(sprintf('voc4-%s',model_name),n);
		fname_2 = sprintf('%s/%s/%04d/%s.predicted_boxes',dirname,videoname,frame,n);
		try
		    % tmp_b{1} = dlmread(fname_1,' ');
		    % [t2,l2,b2,r2,f2,c2] = read_single_frame_boxes(tmp_b,1);
		    [t2,l2,b2,r2,f2,c2] = textread(fname_2,'%f%f%f%f%f%f');
		catch exception
		    fprintf([fname_2 '\n']);
		    rethrow(exception);
		end
		all_boxes{model_i}{frame} = [all_boxes{model_i}{frame};[t2,l2,b2,r2,f2,c2]];
	    end
	end
	if (cellfun(@isempty,all_boxes{model_i}))
	    m_empty_num = m_empty_num + 1;
	else
	    ct(model_i) = boxes_conf_thresh(all_boxes{model_i}) % debugging ;
	    load([model_dirname '/' n(6:end) model_ext]);
	    vt(model_i) = min((model.thresh - 0.4),ct(model_i)) % debugging ;
	end
    end
    m_empty_num
    if ~exist('vt','var')
	fprintf('This model has no detections (vt error)\n');
	return;
    end
    max_thresh = max(vt);
    possible_boxes{length(dir_frames)} = [];
    for m = 1:(num_models-m_empty_num)
	thresh_diff = max_thresh - vt(m);
	for frame = 1:length(dir_frames)
	    [t,l,b,r,f,c] = read_single_frame_boxes(all_boxes{m},frame);
	    c = c + thresh_diff;
	    possible_boxes{frame} = [possible_boxes{frame}; [t,l,b,r,f,c]];
	end
    end

    viterbi_thresh = max_thresh;

    % conf_thresh = boxes_conf_thresh(possible_boxes);
    % % hardcoded offset to allow full-video tracking
    % viterbi_thresh = min((model.thresh - 0.4),conf_thresh)

    fprintf('Viterbi Tracking:\n');
    [path, start, finish] = getshortestcostpath([dirname '/'],videoname,possible_boxes,viterbi_thresh);
    if (start == -1)
	fprintf('This model has no detections (1st track)\n');
	return;
    end
    [tracked_boxes,smoothed_boxes] = extract_path(path,finish);
    previous_tracked_boxes = tracked_boxes;

    fprintf('Write tracked boxes 1:\n');
    write_boxes_to_disk(tracked_boxes,smoothed_boxes,dirname,videoname,model_name,1);
    new_boxes = suppress_tracked_boxes(tracked_boxes,possible_boxes,viterbi_thresh);

    instance_num = 2;
    while (instance_num < 4) % sanity check
	[path, start, finish] = getshortestcostpath([dirname '/'],videoname,new_boxes,viterbi_thresh);
	if (start == -1)
	    break;
	end
	[tracked_boxes,smoothed_boxes] = extract_path(path,finish);
	current_tracked_boxes = tracked_boxes;
	[b,r] = continue_viterbi(previous_tracked_boxes,current_tracked_boxes,viterbi_thresh)
	previous_tracked_boxes = [previous_tracked_boxes current_tracked_boxes];
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
	for j = 1:6:size(previous_tracked_boxes,2)
	    if boxes_close(current_tracked_boxes(i,:),previous_tracked_boxes(i,j:j+5)) ...
		    || boxes_close(previous_tracked_boxes(i,j:j+5),current_tracked_boxes(i,:))
		current_tracked_boxes(i,6) = thresh;
	    end
	end
    end
    c = current_tracked_boxes(:,6);
    l1 = length(c(c(:)>-intmax));
    l2 = length(c(c(:)>thresh));
    r = l2/l1;
    if (r > 0.7)
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
    conf_thresh = graythresh(legal_max_conf + abs(min_conf)) - abs(min_conf);
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
	dlmwrite(sprintf('%s/%s/%04d/voc4-%s-%d.tracked_box',dirname,videoname,i,model_name,num)...
		 ,tracked_boxes(i,:),'delimiter',' ');
	dlmwrite(sprintf('%s/%s/%04d/voc4-%s-%d.smooth_tracked_box',dirname,videoname,i,model_name,num)...
		 ,smoothed_boxes(i,:),'delimiter',' ');
    end
end
