% set up directories
%-----------------------------------------------------------------
if ~exist('exp', 'var')
    exp = 1;
end;
if ~exist('suffix', 'var')
    suffix = [];   % should match taht one in generate_cascade_trainingdata_script
end;

if ~exist('thresh_diff', 'var')
    thresh_diff = -1.1;
end;

% different experiments that have been run
switch exp
    case 1
        % original batch of models, annotated by Dhaval
        cdname ='b';
        originalmodel_dir = '/net/perisikan/aux/salvi/tmp-video-old/C-D1b/usc29/models/';   % models for original detector
        if ~isempty(suffix)
            csc_folder = ['/net/upplysingaoflun/aux/home/fidler/cascade_training_' suffix '/'];
        else
            csc_folder = '/net/upplysingaoflun/aux/home/fidler/cascade_training/';    % where to output training files and cascade models
        end;
        
        %video_root = ['/net/perisikan/aux/fidler/tmp-video-datasets/C-D1b/videos/'];    % directory where the video frames are
        video_root = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1/recognition/';
        if ~isempty(suffix)
            trainingfiles_dir = ['/net/upplysingaoflun/aux/home/fidler/data/C-D1' cdname '/training_data_' suffix '/'];
        else
            trainingfiles_dir = ['/net/upplysingaoflun/aux/home/fidler/data/C-D1' cdname '/training_data/']; 
        end;
        
        % %s is for model_name
        modelfileformat = [originalmodel_dir '%s.mat'];
    case 2
        % newly trained models on data annotated by Zach
        %cdname = [];
        cdname = 'b';
        %originalmodel_dir = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1models/';
        originalmodel_dir = '/net/perisikan/aux/salvi/tmp-video-datasets/run2/';
        if isempty(suffix)
           csc_folder = '/net/perisikan/aux/fidler/cascade_training/';
        else
           csc_folder = ['/net/perisikan/aux/fidler/cascade_training_' suffix '/']; 
        end;
        if strcmp(cd, 'b')
           %video_root = ['/net/perisikan/aux/fidler/tmp-video-datasets/C-D1b/videos/'];    % directory where the video frames are 
           video_root = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1b/videos';
           %video_root = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1/recognition/';
        else
           video_root = '/net/perisikan/aux/salvi/tmp-video-datasets/C-D1/recognition/';
        end;
        if isempty(suffix)
            trainingfiles_dir = ['/net/perisikan/aux/fidler/data/C-D1' cdname '/training_data/'];
        else
            trainingfiles_dir = ['/net/perisikan/aux/fidler/data/C-D1' cdname '/training_data_' suffix '/'];
        end;
        
        modelfileformat = [originalmodel_dir '%s/%s_final.mat'];
end;
%cascade_suffix = '-cascade';
cascade_suffix = '';
%-----------------------------------------------------------------

[files] = find_modelfiles(originalmodel_dir);
all_models = [];
for i = 1 : length(files)
    [path, name, ext] = fileparts(files(i).name);
    all_models = [all_models, {name}];
end;

if ~exist('ind_models', 'var')
    ind_models = [1:length(all_models)];
end;

%if ~exist(model_name, 'var')
%    model_name = 'person';
%end;
for j = 1 : size(ind_models)
    try
    i = ind_models(j);
    model_name = all_models{i};
    model_file = sprintf(modelfileformat, model_name);   % link to original model
    % some experiments have different setups in folders, so check a bit:
    [path1, name, ext] = fileparts(model_file);
    if ~strcmp(ext, '.mat')
        model_file = sprintf(modelfileformat, model_name, model_name);
    end;

    if ~exist(csc_folder, 'dir')
        mkdir(csc_folder);
    end;
    cscmodel_file = fullfile(csc_folder, [model_name cascade_suffix '.mat']);
    load(model_file);

    if ~exist([csc_folder '/annotations/' model_name '-cascade.mat'], 'file')
        model_dir = fullfile(trainingfiles_dir, model_name);
        createDataMatrixCascade(video_root, model_dir, fullfile(csc_folder, 'annotations'), model_name);
    end;

    disp('----------------')
    fprintf('Training the model for ''%s''\n', model_name)
    fprintf('All training output to dir: %s\n', csc_folder)
    fprintf('Using thresh diff of %0.2f\n', thresh_diff)
    fprintf('Original model files: %s\n', model_file)
    if ~exist(cscmodel_file, 'file')
       csc_model = cascade_train_main(model_file, csc_folder, cscmodel_file, model_name, thresh_diff);
    end;
    catch err
        disp(err);
    end;
end;

clear;