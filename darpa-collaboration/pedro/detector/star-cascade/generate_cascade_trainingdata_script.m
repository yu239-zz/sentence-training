% this script generates a file with training boxes for the cascade detector
% with reduced threshold (original models' thresh + thresh_diff)
% script calls generete_cascade_trainingdata, which:
%    - takes original training boxes and takes a bigger box around it
%    (right now it's 2.2 times bigger in both directions -- regulated with variable 'extend_factor')
%  set ind_models in the console for which models you want to run the script on
% set exp in the console, which is the experiment number, see below for more info


% set up directories
%-----------------------------------------------------------------
if ~exist('exp', 'var')
    exp = 1;
end;
if ~exist('suffix', 'var')
    suffix = [];
end;
if ~exist('thresh_diff', 'var')
    thresh_diff = -1.2;   % difference with originally learned model's threshold
end;
if ~exist('nms_thresh', 'var')
    nms_thresh = 0.8;
end;
if ~exist('max_per_frame', 'var')
    max_per_frame = 11;
end;
if ~exist('extend_factor', 'var')
    extend_factor = 0.6;
end;

% different experiments that have been run
switch exp
    case 1
        % original batch of models, annotated by Dhaval
        cdname = 'b';
        %frames_dir =
        %'/net/perisikan/aux/fidler/tmp-video-datasets/C-D1b/videos/';   % training frames  
        frames_dir = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1/recognition/';
        %training_dir = '/net/upplysingaoflun/aux/home/salvi/darpa-collaboration/data/C-D1b/training-data/'; % 
        training_dir = '~/darpa-collaboration/data/C-D1b/training-data/'; % 
        originalmodel_dir = '/net/perisikan/aux/salvi/tmp-video-old/C-D1b/usc29/models/';
        
        if ~isempty(suffix)
            output_dir = ['/net/upplysingaoflun/aux/home/fidler/data/C-D1' cdname '/training_data_' suffix '/'];
        else
            output_dir = ['/net/upplysingaoflun/aux/home/fidler/data/C-D1' cdname '/training_data/'];
        end;
        
        % %s is for model_name
        datafileformat = [training_dir '%s/%s-positives.text'];
        outputfileformat = [output_dir '%s/%s-cascade-positives.txt'];
        modelfileformat = [originalmodel_dir '%s.mat'];
    case 2
        % newly trained models on data annotated by Zach
        %cdname = [];
        cdname = 'b';
        frames_dir = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1/recognition/';
        %frames_dir = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1b/videos';
        %training_dir = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1models/';
        %originalmodel_dir = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1models/';
        training_dir = '/net/perisikan/aux/salvi/tmp-video-datasets/run3/';
        originalmodel_dir = training_dir;
        if ~isempty(suffix)
            output_dir = ['/net/perisikan/aux/fidler/data/C-D1' cdname '/training_data_' suffix '/'];
        else
            output_dir = ['/net/perisikan/aux/fidler/data/C-D1' cdname '/training_data/'];
        end;
        
        datafileformat = [training_dir '%s/%s-positives.text'];
        outputfileformat = [output_dir '%s/%s-cascade-positives.txt'];
        modelfileformat = [originalmodel_dir '%s/%s_final.mat'];
end;
%-----------------------------------------------------------------


% find all models for original detectors (cascade training will work with
% this models)
%-----------------------------------------------------------------
[files] = find_modelfiles(originalmodel_dir);

if ~exist('ind_models', 'var')
    ind_models = [1 : length(files)];
end;
%-----------------------------------------------------------------


% cascade training, ind_models indexes into files variable
for j = 1 : size(ind_models, 1)
    i = ind_models(j);
    [path1, model_name, ext] = fileparts(files(i).name);

data_file = sprintf(datafileformat, model_name, model_name); % training boxes for the model
output_file = sprintf(outputfileformat, model_name, model_name);   % file to output new training data
model = sprintf(modelfileformat, model_name);   % link to original model
% some experiments have different setups in folders, so check a bit:
[path1, name, ext] = fileparts(model);
if ~strcmp(ext, '.mat')
    model = sprintf(modelfileformat, model_name, model_name);
end;

[path, name, ext] = fileparts(output_file);
if ~exist(path, 'dir')
    try
        fprintf('Generating path: %s\n', path)
       mkdir(path);
    catch err,
        disp(err)
    end;
end;

% expand-video-list filelist.text
if ~exist(output_file, 'file')
   fprintf('Generating training data for model ''%s''\n', model_name)
   fprintf('Taking video frames from %s...\n', data_file)
   try
   generete_cascade_trainingdata(frames_dir, data_file, output_file, model, thresh_diff, nms_thresh, extend_factor, max_per_frame)
   catch err
       disp(err)
   end;
else
    fprintf('Training file already exists! skipping...\n');
end;
end;

clear;