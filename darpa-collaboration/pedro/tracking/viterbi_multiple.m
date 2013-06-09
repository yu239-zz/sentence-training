function viterbi_multiple(videoname,model_name,method,lk_ahead,topn)
% Method - 0: standard optical flow
%          1: KLT tracker
% Finds the best box from a particular sequence for each frame using viterbi on
% detector output with higher false positives
% TODO: forward and backward optical-flow/KLT (will require renaming save files)

    global model_threshold_offset;
    global smooth_center_x_pieces;
    global smooth_center_y_pieces;
    global smooth_width_pieces;
    global smooth_height_pieces;
    global max_quantile_threshold;
    global supress_tracked_boxes_quantile;
    global above_threshold_ratio;
    global max_nr_of_tracks;
    global number_of_elements_in_box;

    global filter_length;
    global filter_box_jump;
    global filter_swaps;
    global filter_short_swaps;

    model_threshold_offset = 0.4;
    smooth_center_x_pieces = 10;
    smooth_center_y_pieces = 10;
    smooth_width_pieces = 10;
    smooth_height_pieces = 10;
    max_quantile_threshold = 1;
    supress_tracked_boxes_quantile = 0.25;
    above_threshold_ratio = 0.7;
    max_nr_of_tracks = 4;
    number_of_elements_in_box = 8;

    addpath('~/darpa-collaboration/ideas')
    addpath('~/darpa-collaboration/optical-flow/bilgic')

    if (nargin < 5) topn=12; end;

    [dirname, name, ext] = fileparts(videoname);
    if isempty(dirname)
	dirname = [getenv('HOME') '/video-datasets/C-D1a/SINGLE_VERB'];
    end
    videoname = [name ext];

    [model_dirname, model_name, model_ext] = fileparts(model_name);
    if isempty(model_ext) model_ext = '.mat'; end;
    if isempty(model_dirname)
	model_dirname = [getenv('HOME') '/video-datasets/C-D1a/voc4-models'];
    end

    display(videoname);
    dir_frames = dir_regexp([dirname '/' videoname],'^[0-9]+$');


    filter_length = -inf;
    %	filter_length = length(dir_frames) / 10;
    filter_box_jump = inf;
    %	filter_box_jump = 150;
    filter_swaps = inf;
    %	filter_swaps = 10;
    filter_short_swaps = inf;
    %	filter_short_swaps = 4;

    [model_dirname '/' model_name model_ext]
    load([model_dirname '/' model_name model_ext])

    switch method
      case 0
	get_opticalflow(dirname, videoname);
      case 1
	if (exist([dirname, '/', videoname, '/0001/klt-current.txt']) ...
            && exist([dirname, '/' videoname, '/0001/klt-next.txt']))
	    disp('Using precomputed KLT files..');
	else
	    disp('Precomputing KLT files..');
	    unix(sprintf(['~/darpa-collaboration/pedro/tracking/preprocess_klt.sh %s'], ...
                         [dirname '/' videoname]));
	end
	% TODO: getshortestcostpath.m uses standard optical flow regardless
	get_opticalflow(dirname, videoname);
      otherwise
	disp('Method not handled');
    end

    for frame=1:length(dir_frames)
	sprintf('%s/%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name);
	display(sprintf('%s/%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name));
	[t,l,b,r,f,c] = textread(sprintf('%s/%s/%04d/voc4-%s.boxes', ...
                                         dirname,videoname,frame,model_name),'%f%f%f%f%f%f');
	[temp,temp_idx]=sort(c,'descend');
	temp=[t,l,b,r,f,c,zeros(size(t,1),1),zeros(size(t,1),1)];
	temp=temp(temp_idx,:);
 	boxes{frame} = temp(1:min(topn,size(temp,1)),:);
    end

    fprintf('Box prediction:\n');
    possible_boxes = predictboxes(dirname, videoname, boxes, method, lk_ahead);
    for i=1:length(dir_frames)
	added_boxes{i} = setdiff(possible_boxes{i},boxes{i},'rows');
	sprintf('%s/%s/%04d/voc4-%s.predicted_boxes',dirname,videoname,i,model_name);
        fid = fopen(sprintf('%s/%s/%04d/voc4-%s.predicted_boxes', ...
                            dirname,videoname,i,model_name), 'w');
        % fprintf(fid, '%ld %ld %ld %ld %ld %ld %ld %ld\n', added_boxes{i}');
    for l=1:size(added_boxes{i},1)
	predicted_output = added_boxes{i};
	fprintf(fid, '%ld %ld %ld %ld %ld %ld %ld %s\n',predicted_output(l,1:7),model_name);
    end
	fclose(fid);
    end

    save(sprintf('%s/%s/%s-boxes',dirname,videoname,model_name),'possible_boxes');
    unix(sprintf('chmod 664 %s/%s/%s-boxes.mat',dirname,videoname,model_name));
    load(sprintf('%s/%s/%s-boxes',dirname,videoname,model_name));

    dirlist = dir([dirname '/' videoname '/0001' '/*' model_name '*.boxes']);
    display([dirname videoname '/0001' '/*' model_name '*.boxes']);
    num_models = length(dirlist) %debugging ;
    length(dir_frames) % debugging
    dirlist.name % debugging

    m_empty_num = 0;
    model_name_lut = {};

    num_models
    for m = 1:num_models
	model_i = m - m_empty_num;
	[p,n,e] = fileparts(dirlist(m).name);
        model_name_lut{m,1} = n(6:end);
	for frame = 1:length(dir_frames)
	    fname_1 = sprintf('%s/%s/%04d/%s.boxes',dirname,videoname,frame,n);
	    try
		[t,l,b,r,f,c] = textread(fname_1,'%f%f%f%f%f%f');
	    catch exception
		fprintf([fname_1 '\n']);
		rethrow(exception);
            end
            [temp,temp_idx]=sort(c,'descend');
            temp=[t,l,b,r,f,c,zeros(size(t,1),1),ones(size(t,1),1) .* m];
            % jw: added to cull the number of boxes
            temp=temp(temp_idx,:);
	    all_boxes{model_i}{frame} = temp(1:min(topn,size(temp,1)),:);
	    if strcmp(sprintf('voc4-%s',model_name),n);
		fname_2 = sprintf('%s/%s/%04d/%s.predicted_boxes',dirname,videoname,frame,n);
		try
		    [t2,l2,b2,r2,f2,c2,offset2,model_num2] = textread(fname_2,'%f%f%f%f%f%f%f%s');
		catch exception
		    fprintf([fname_2 '\n']);
		    rethrow(exception);
		end
		all_boxes{model_i}{frame} = ...
                    [all_boxes{model_i}{frame};[t2,l2,b2,r2,f2,c2,offset2,ones(size(t2,1),1) .* m]];
	    end
	end
	if (cellfun(@isempty,all_boxes{model_i}))
	    m_empty_num = m_empty_num + 1;
	else
	    ct(model_i) = boxes_conf_thresh(all_boxes{model_i});
	    display([model_dirname '/' n(6:end) model_ext]);
	    load([model_dirname '/' n(6:end) model_ext]);
	    vt(model_i) = min((model.thresh - model_threshold_offset),ct(model_i));
	end
    end

    max_thresh = max(vt);
    possible_boxes = {};
    possible_boxes{length(dir_frames)} = [];

    for m = 1:(num_models-m_empty_num)
	thresh_diff = max_thresh - vt(m);
	for frame = 1:length(dir_frames)
	    [t,l,b,r,f,c,offset,model_num] = read_single_frame_boxes(all_boxes{m},frame);
	    c = c + thresh_diff;
	    possible_boxes{frame} = [possible_boxes{frame}; [t,l,b,r,f,c,offset,model_num]];
	end
    end

    viterbi_thresh = max_thresh;

    fprintf('Viterbi Tracking:\n');
    [path, start, finish] = getshortestcostpath(dirname,videoname,possible_boxes,viterbi_thresh);
    [tracked_boxes,smoothed_boxes] = extract_path(path,finish);
    previous_tracked_boxes = tracked_boxes;

    fprintf('Write tracked boxes 1:\n');
    write_boxes_to_disk(tracked_boxes,smoothed_boxes,dirname,videoname,model_name,1,model_name_lut);
    new_boxes = suppress_tracked_boxes(tracked_boxes,possible_boxes,viterbi_thresh);

    instance_num = 2;
    while (instance_num < max_nr_of_tracks) % sanity check
	[path, start, finish] = getshortestcostpath(dirname,videoname,new_boxes,viterbi_thresh);
	if (start == -1) break; end;
	[tracked_boxes,smoothed_boxes] = extract_path(path,finish);
	current_tracked_boxes = tracked_boxes;
	[b,r] = continue_viterbi(previous_tracked_boxes,current_tracked_boxes,viterbi_thresh)

        box_jump = max_box_jump(current_tracked_boxes);
        changes = number_model_changes(current_tracked_boxes);
        display(['Box jump: ' num2str(box_jump)]);
        display(['Track length: ' num2str(finish-start)]);
        display(['Number total model changes: ' num2str(length(changes))]);
        display(['Number short model changes: ' num2str(length(find(changes<4)))]);

	previous_tracked_boxes = [previous_tracked_boxes current_tracked_boxes];

	if ~b || (finish-start) < filter_length || box_jump > filter_box_jump ...
              || length(changes) > filter_swaps || length(find(changes<3)) > filter_short_swaps
	    fprintf('Terminating..!\n');
	    break;
	end
	fprintf('Write tracked boxes:\n');
	write_boxes_to_disk(tracked_boxes,smoothed_boxes,dirname, ...
                            videoname,model_name,instance_num,model_name_lut);
	instance_num = instance_num + 1;
	fprintf('%d\n',instance_num);
	new_boxes = suppress_tracked_boxes(tracked_boxes,new_boxes,viterbi_thresh);
    end
    fprintf('Tracking Done.\n');
end

function model_runs = number_model_changes(current_tracked_boxes)
    num = 0;
    model_num = [];
    for i=1:size(current_tracked_boxes,1)-1
        model_num = [model_num; current_tracked_boxes(i,8)-current_tracked_boxes(i+1,8)];
    end
    model_idx = find(abs(model_num) > 0);
    model_runs = [];
    for i=1:size(model_idx,1)-1
        model_runs = [model_runs ; model_idx(i+1) - model_idx(i)];
    end
end

function box_jump = max_box_jump(current_tracked_boxes)
    box_jump = 0;
    for i = 1:length(current_tracked_boxes)-1
        t0 = current_tracked_boxes(i,1);
        l0 = current_tracked_boxes(i,2);
        b0 = current_tracked_boxes(i,3);
        r0 = current_tracked_boxes(i,4);
        t1 = current_tracked_boxes(i+1,1);
        l1 = current_tracked_boxes(i+1,2);
        b1 = current_tracked_boxes(i+1,3);
        r1 = current_tracked_boxes(i+1,4);
        center0 = [(t0+b0)/2 (l0+r0)/2];
        center1 = [(t1+b1)/2 (l1+r1)/2];
        box_jump = max(sqrt(double((center0(1)-center1(1))^2+(center0(2)-center1(2))^2)),box_jump);
    end
end

function [b,r] = continue_viterbi(previous_tracked_boxes,current_tracked_boxes,thresh)
    global above_threshold_ratio;
    global number_of_elements_in_box;
    for i = 1:length(current_tracked_boxes)
	for j = 1:8:size(previous_tracked_boxes,2)
	    jump = number_of_elements_in_box-1;
	    if boxes_close(current_tracked_boxes(i,:),previous_tracked_boxes(i,j:j+jump)) ...
		    || boxes_close(previous_tracked_boxes(i,j:j+jump),current_tracked_boxes(i,:))
		current_tracked_boxes(i,6) = thresh;
	    end
	end
    end
    c = current_tracked_boxes(:,6);
    l1 = length(c(c(:)>-intmax));
    l2 = length(c(c(:)>thresh));
    r = l2/l1;
    if (r > above_threshold_ratio)
	b = 1;
    else
	b = 0;
    end
end

function new_boxes = suppress_tracked_boxes(tracked_boxes,boxes,thresh)
    global max_quantile_threshold;
    global supress_tracked_boxes_quantile;

    for i = 1:length(tracked_boxes)
	[t,l,b,r,f,c,offset,model_num] = read_single_frame_boxes(boxes,i);
	if max_quantile_threshold
	    rep_c = max(quantile(c,supress_tracked_boxes_quantile),thresh);
	else
	    rep_c = quantile(c,supress_tracked_boxes_quantile);
	end
	for j = 1:length(c)
	    if boxes_close(tracked_boxes(i,:),[t(j,1) l(j,1) b(j,1) r(j,1)])
		c(j,1) = min(rep_c,c(j,1));
	    end
	end
	new_boxes{i} = [t,l,b,r,f,c,offset,model_num];
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

function [t,l,b,r,f,c,offset,model_num] = read_single_frame_boxes(boxes, index)
    single_box = boxes{index};
    t = single_box(:,1);
    l = single_box(:,2);
    b = single_box(:,3);
    r = single_box(:,4);
    f = single_box(:,5);
    c = single_box(:,6);
    offset = single_box(:,7);
    model_num = single_box(:,8);
end

function conf_thresh = boxes_conf_thresh(boxes)
    for i = 1:length(boxes)
	max_col_boxes = max(boxes{i},[],1);
	if (length(max_col_boxes) == 0)
	    max_conf(i) = intmax; % defaut is 32bit Int
	else
	    max_conf(i) = max_col_boxes(6);
	end
    end
    legal_max_conf = double(max_conf(max_conf(:)<intmax));
    min_conf = min(legal_max_conf);
    conf_thresh = graythresh(legal_max_conf - min_conf) + min_conf;
end

function [tracked_boxes,smoothed_boxes] = extract_path(path,finish)

    global smooth_center_x_pieces;
    global smooth_center_y_pieces;
    global smooth_width_pieces;
    global smooth_height_pieces;

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
            offset{i} = path{i}.filter(1,:);
            model_num{i} = path{i}.model_num(1,:);
	    tracked_boxes(i,:) = ...
                [flip_box(path{i}.box(1,:)) filter{i} conf{i} offset{i} model_num{i}];
	else
	    if (i == finish)
		skip(i) = 0;
		filter{i} = path{i}.filter(finish_box_index,:);
		conf{i} = path{i}.conf(finish_box_index,:);
                offset{i} = path{i}.offset(finish_box_index,:);
                model_num{i} = path{i}.model_num(finish_box_index,:);
		tracked_boxes(i,:) = [flip_box(path{i}.box(finish_box_index,:)) ...
                                    filter{i} conf{i} offset{i} model_num{i}];
                % centres are half-sized
		centres(i,:) = flip_point((path{i}.center(finish_box_index,:)).*2);
	    else
		skip(i) = 0;
		filter{i} = path{i}.filter(pre_p,:);
		conf{i} = path{i}.conf(pre_p,:);
                offset{i} = path{i}.offset(pre_p,:);
                model_num{i} = path{i}.model_num(pre_p,:);
		tracked_boxes(i,:) = [flip_box(path{i}.box(pre_p,:)) ...
                                    filter{i} conf{i} offset{i} model_num{i}];
                % centres are half-sized
		centres(i,:) = flip_point((path{i}.center(pre_p,:)).*2);
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
    ppx = splinefit(new_ts,new_centres(:,1),smooth_center_x_pieces,4);
    ppy = splinefit(new_ts,new_centres(:,2),smooth_center_y_pieces,4);
    smx = ppval(ppx,ts);
    smy = ppval(ppy,ts);
    ppw = splinefit(new_ts,new_widths(:),smooth_width_pieces,4);
    pph = splinefit(new_ts,new_heights(:),smooth_height_pieces,4);
    smw = ppval(ppw,ts);
    smh = ppval(pph,ts);

    for i=1:num_f
	if (path{i}.flag == 0)
	    smoothed_boxes(i,:) = tracked_boxes(i,:);
	else
	    smoothed_boxes(i,:) = ...
                [smx(i)-round(smw(i)/2) smy(i)-round(smh(i)/2) ...
                 smx(i)+round(smw(i)/2) smy(i)+round(smh(i)/2) ...
                 filter{i} conf{i} offset{i} model_num{i}];
	end
    end
end

function d = write_boxes_to_disk(tracked_boxes,smoothed_boxes,dirname,videoname,model_name,num,lut)
    for i = length(tracked_boxes):-1:1
        %zzq debugging
	display(sprintf('%s/%s/%04d/voc4-%s-%d.tracked_box',dirname,videoname,i,model_name,num));
        fid = fopen(sprintf('%s/%s/%04d/voc4-%s-%d.tracked_box',dirname,videoname,i,model_name,num), 'w');
	if tracked_boxes(i,8) == -1
	    fprintf(fid, '%ld %ld %ld %ld %ld %ld %ld %s\n', tracked_boxes(i,1:7),'none');
	else
	    fprintf(fid, '%ld %ld %ld %ld %ld %ld %ld %s\n', tracked_boxes(i,1:7),lut{tracked_boxes(i,8)});
	end
        fclose(fid);
        fid = fopen(sprintf('%s/%s/%04d/voc4-%s-%d.smooth_tracked_box',dirname,videoname,i,model_name,num), 'w');
	if smoothed_boxes(i,8) == -1
            fprintf(fid, '%ld %ld %ld %ld %ld %ld %ld %s\n', smoothed_boxes(i,1:7),'none');
	else
            fprintf(fid, '%ld %ld %ld %ld %ld %ld %ld %s\n', smoothed_boxes(i,1:7),lut{smoothed_boxes(i,8)});
	end
        fclose(fid);
    end
end
