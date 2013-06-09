function detect_darpa_video(videoname,model_name,thresh_diff,nms_thresh,frame_num)
% Runs Pedro's detector on all the frames of a video
% thresh_diff is the amount added to model-threshold to get
% more false positives
% nms_thresh is to control non-maximal suppression

    addpath('~/darpa-collaboration/ideas')

    [dirname, name, ext] = fileparts(videoname);
    if isempty(dirname)
	dirname = [getenv('HOME') '/video-datasets/C-D1a/SINGLE_VERB/'];
    end
    videoname = [name ext];

    [model_dirname, model_name_save, model_ext] = fileparts(model_name);
    if isempty(model_ext)
	model_ext = '.mat'
    end
    if isempty(model_dirname)
	model_dirname = [getenv('HOME') '/video-datasets/C-D1a/voc4-models/'];
    end
    model_name = [model_name_save model_ext];

    display(videoname);
    dir_frames = dir_regexp([dirname '/' videoname],'^[0-9]+$');
    load([model_dirname '/' model_name]);
    model = bboxpred_dummy(model);
length(dir_frames)
	
	if nargin < 5
		frame_num=1:length(dir_frames);
	end

    for frame=frame_num
	if (exist(sprintf('%s/%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name_save)))
		disp(['Using precomputed boxes for frame ' num2str(frame)])
	else
	    fprintf('frame: %04d\n',frame);
	    im = imread(sprintf('%s/%s/%04d/frame.ppm',dirname,videoname,frame));
	    boxes = process(im,model,(model.thresh + thresh_diff),nms_thresh);
	    % showboxes(im, boxes);
	    fid = fopen(sprintf('%s/%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name_save), 'w');
	    fprintf(fid, '%ld %ld %ld %ld %ld %ld\n', boxes');
	    fclose(fid);
	end
    end
