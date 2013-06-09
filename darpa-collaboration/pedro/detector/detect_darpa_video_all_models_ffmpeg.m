function detect_darpa_video_all_models_ffmpeg(videoname,model_folder,thresh_diff,nms_thresh,model_list)
% Runs Pedro's detector on all the frames of a video
% thresh_diff is the amount added to model-threshold to get
% more false positives
% nms_thresh is to control non-maximal suppression

[status,timestamp] = system('date +%s');
fprintf('starting %s\n',timestamp);

if (nargin < 3)
    thresh_diff = 0;
end;
if (nargin < 4)
    nms_thresh = 0.8;
end;
if (nargin < 5)
	model_list={};
end
timeit = 1;

addpath('~/darpa-collaboration/ideas')
addpath('~/darpa-collaboration/pedro/detector')
addpath('~/darpa-collaboration/pedro/detector/star-cascade')
addpath('~/darpa-collaboration/ffmpeg/')
if isempty(model_folder)
	   model_folder = [getenv('HOME') '/video-datasets/C-D1a/voc4-models/'];
    end
    [dirname, name, ext] = fileparts(videoname);
    if isempty(dirname)
	   dirname = [getenv('HOME') '/video-datasets/C-D1/recognition/'];
    end
    videoname = [name ext];

    models = load_models(model_folder,model_list);    % load all cascade models in a folder model_folder
    [padx, pady, sbin, interval, coeff, dopca] = get_pyrinfo_allmodels(models); % compute image padding, sbin and interval for all models

	ffmpegOpenVideo([ dirname '/' videoname '.mov']);
	frame=1;
	while(not(ffmpegIsFinished()))
	[e, e_models] = check_existing_boxes(dirname, videoname, frame, models);
	if (e == 0)
			system([ 'mkdir -p ' sprintf('%s/%s/%04d/',dirname,videoname,frame) ]);
	   fprintf('frame %04d\n',frame);
	   im=double(ffmpegGetFrame());

	   if (timeit) th = tic; end;
	   pyra = featpyramid2(im, padx, pady, sbin, interval);
	   if (timeit)
	      t = toc(th);
	      fprintf('time: pyramid %0.4f\n', t);
	   end;

	   [ind] = find(e_models == 0);  % for which models detections don't exist

	   for j = 1 : numel(ind)
		i_model = ind(j);
		model_name = models{i_model}.filename;

		if (timeit) th = tic; end;
		[boxes, all] = gdetect(pyra, models{i_model}, models{i_model}.thresh + thresh_diff);
		[boxes, all] = get_nms_boxes(im, models{i_model}, boxes, all, nms_thresh);
		if (timeit)
		   t = toc(th);
		   fprintf('time: model %s %0.4f\n', model_name,t);
		end;
		dlmwrite(sprintf('%s/%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name), all, ' ');
	   end;
	end;
		frame=ffmpegNextFrame();
    end
	ffmpegCloseVideo();

	[status,timestamp] = system('date +%s');
	fprintf('finished %s\n',timestamp);
end

function models = load_models(model_folder,model_list)
% stores all cascade models in a folder to a cell array

%model_list
model_files = dir(fullfile(model_folder, '*.mat'));
models = cell(length(model_files),1);
pntr = 1;

for i = 1 : length(model_files)
    if ~isempty(model_list)
	valid_model=false;
	for j = 1:length(model_list)
	    model_name=model_files(i).name;
	    model_name=model_name(1:length(model_name)-4);
	    if strcmp(model_name,model_list{j})
		valid_model=true;
	    end
	end
	if ~valid_model
	    continue;
	end
    end
    data = load(fullfile(model_folder, model_files(i).name));
    if (isfield(data, 'csc_odel'))
	model = data.model;
	[path, name, ext] = fileparts(model_files(i).name);
	model.filename = name;
	models{pntr} = model;
	pntr = pntr + 1;
    end;
end;

csc_models = csc_models(1 : pntr - 1);

fprintf('found models:')
display(intersect(cellfun(@(m) m.filename, csc_models, 'UniformOutput', false), model_list))
fprintf('missing models:')
display(setdiff(model_list, cellfun(@(m) m.filename, csc_models, 'UniformOutput', false)))

end


function [padx, pady, sbin, interval, coeff, dopca] = get_pyrinfo_allmodels(models)
% computes max padding for all models

padx = 0;
pady = 0;
sbin = 0;
interval = 0;
dopca = 1;
coeff = 0;

for i = 1 : length(models)
    [padx_i, pady_i] = getpadding(models{i});
    padx = max(padx, padx_i);
    pady = max(pady, pady_i);
    sbin = max(sbin, models{i}.sbin);
    interval = max(interval, models{i}.interval);
    if (i == 1)
	if (isfield(models{i}, 'coeff'))
	   coeff = models{i}.coeff;
	else
	    dopca = 0;
	end;
    else
	if (dopca)
	   coeff_i = models{i}.coeff;
	   if ~(all(coeff == coeff_i))
	       dopca = 0;
	       coeff = 0;
	   end;
	end;
    end;
end;

end

function [e, e_models] = check_existing_boxes(dirname, videoname, frame, models)
% checks if detections for all models exist
% e = 1 if detections for all models exist, e = 0 if detections for at least one model are missing
% e_models is a vector with e_models(i)=1 if detections for i-the model
% exist and 0 otherwise

e_models = zeros(length(models), 1);

for i = 1 : length(models)
    e_models(i) = exist(sprintf('%s/%s/%04d/voc4-%s.boxes',dirname,videoname,frame,models{i}.filename));
end;

e = min(double(e_models));
end
