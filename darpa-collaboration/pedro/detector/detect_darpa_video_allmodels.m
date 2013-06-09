function detect_darpa_video_allmodels(videoname,model_folder,thresh_diff,nms_thresh)
% Runs Pedro's detector on all the frames of a video
% thresh_diff is the amount added to model-threshold to get
% more false positives
% nms_thresh is to control non-maximal suppression

if (nargin < 3)
    thresh_diff = 0;
end;
if (nargin < 4)
    nms_thresh = 0.8;
end;    
timeit = 1;

addpath('~/darpa-collaboration/ideas')
addpath('~/darpa-collaboration/pedro/detector')
addpath('~/darpa-collaboration/pedro/detector/star-cascade')

    if isempty(model_folder)
	   model_folder = [getenv('HOME') '/video-datasets/C-D1a/voc4-models/'];
    end
    [dirname, name, ext] = fileparts(videoname);
    if isempty(dirname)
	   dirname = [getenv('HOME') '/video-datasets/C-D1a/SINGLE_VERB/'];
    end
    videoname = [name ext];
    

   
    models = load_models(model_folder);    % load all models in a folder model_folder
    [padx, pady, sbin, interval, coeff, dopca] = get_pyrinfo_allmodels(models); % compute image padding, sbin and interval for all models

    dir_frames = dir_regexp([dirname '/' videoname],'^[0-9]+$');
    %model = bboxpred_dummy(model);
    fprintf('Total number of frames for video %s: %d\n', videoname, length(dir_frames))

    for frame=1:length(dir_frames)
        [e, e_models] = check_existing_boxes(dirname, videoname, frame, csc_models);
        if (e == 0)
           fprintf('frame: %04d\n',frame);
           imfile = sprintf('%s/%s/%04d/frame.ppm',dirname,videoname,frame); 
           im = double(imread(imfile));
           
           if (timeit) th = tic; end;
           pyra = featpyramid2(im, padx, pady, sbin, interval);
           if (timeit)
              t = toc(th);
              fprintf('Pyramid computation time: %0.4f\n', t);
           end;
           
           [ind] = find(e_models == 0);  % for which models detections don't exist
           
           for j = 1 : numel(ind)
                i_model = ind(j);
                model_name = models{i_model}.filename;
                fprintf('   model: %s\n', model_name)
                
                if (timeit) th = tic; end;
                [boxes, all] = gdetect(pyra, model, models{i_model}.thresh + thresh_diff);
                [boxes, all] = get_nms_boxes(im, models{i_model}, boxes, all, nms_thresh);
                if (timeit)
                   t = toc(th);
                   fprintf('Detection time: %0.4f\n', t);
                end;
                   
                fid = fopen(sprintf('%s/%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name), 'w');
                fprintf(fid, '%ld %ld %ld %ld %ld %ld\n', boxes');
                fclose(fid);

           end;
        
        end;
    end

    
function models = load_models(model_folder)
% stores all cascade models in a folder to a cell array        

model_files = dir(fullfile(model_folder, '*.mat'));
models = cell(length(model_files),1);
pntr = 1;

for i = 1 : length(model_files)
    data = load(fullfile(model_folder, model_files(i).name));
    if (isfield(data, 'model'))
        model = data.model;
        [path, name, ext] = fileparts(model_files(i).name);
        model.filename = name;
        models{pntr} = model;
        pntr = pntr + 1;
    end;
end;

models = models(1 : pntr - 1);


function [padx, pady, sbin, interval] = get_pyrinfo_allmodels(models)
% computes max padding for all models

padx = 0;
pady = 0;
sbin = 0;
interval = 0;


for i = 1 : length(models)
    [padx_i, pady_i] = getpadding(models{i});
    padx = max(padx, padx_i);
    pady = max(pady, pady_i);
    sbin = max(sbin, models{i}.sbin);
    interval = max(interval, models{i}.interval);
end;


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