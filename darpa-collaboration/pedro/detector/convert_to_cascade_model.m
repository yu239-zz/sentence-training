function [model] = convert_to_cascade_model(modelfile, pca, video_dir, training_data_dir, result_dir, tmp_dir)
% Parameters
% + modelfile: filename of a regular (non-cascade) model
% + pca: pca parameter
% + video_dir: where training data movie files can be found
% + training_data_dir: where training data text files can be found
% + result_dir: where to store the resulting casecade model and pca
% statistics files
% + tmp_dir: a directory that this script can abuse. It will be created if
% necessary.
%
% Example:
%
%   modelfile = ['/home/' getenv('USER') '/video-datasets/C-D1/voc4-models-original/person.mat'];
%   video_dir = ['/home/' getenv('USER') '/video-datasets/C-D1/recognition'];
%   training_data_dir = ['/home/' getenv('USER') '/darpa-collaboration/data/C-D1/training-data/' model];
%   result_dir = ['/home/' getenv('USER') '/my-job-results'];
%   tmp_dir = ['/home/' getenv('USER') '/tmp'];
%   generate_cascade_model(modelfile, 5, video_dir, training_data_dir, result_dir, tmp_dir);

% Add relevant paths
    addpath(['/home/' getenv('USER') '/darpa-collaboration/ffmpeg']);
    addpath(['/home/' getenv('USER') '/darpa-collaboration/pedro/detector/star-cascade']);

    % Ensure ffmpeg bindings are built
    if ~exist(['/home/' getenv('USER') '/darpa-collaboration/ffmpeg/libffmpeg-bindings.so'], 'file')
        fprintf(2, 'You must run make successfully within ~/darpa-collaboration/ffmpeg\n');
        error('Ffmpeg bindings not built');
    end

    if ~exist(modelfile, 'file')
        error('Model file doesnt exist');
    end

    load(modelfile);

    model

    if ~isfield(model, 'thresh')
        fprintf(2, 'Failed to find vanilla model in file "%s"\n', modelfile);
        error('Model not found in file');
    end

    modelname = model.class;

    % Make the relevant direcotries
    unix(['mkdir -p "' result_dir '"']);
    unix(['mkdir -p "' tmp_dir '"']);

    % Copy relevant files into tmp_dir
    unix(['cp ' training_data_dir '/' modelname '-* ' tmp_dir '/']);
    unix(['cp ' modelfile ' ' tmp_dir '/' modelname '.mat']);

    % Check we are sane, and ready to go...
    test_file([tmp_dir '/' modelname '.mat']);
    test_file([tmp_dir '/' modelname '-positives.text']);
    test_file([tmp_dir '/' modelname '-negatives.text']);

    % Go ahead with creating temporary files
    pos = createDataMatrixCascade(video_dir, training_data_dir, modelname);

    % Create the cascade model
    cscmodel_file = [tmp_dir '/' modelname '-cascade.mat'];
    model = cascade_train_main(model, result_dir, cscmodel_file, modelname, 0.0, pca, pos);
end

function [status] = test_file(filename)
    if ~exist(filename, 'file')
        fprintf(2, 'File not found: "%s"\n', filename);
        error('File not found');
    end
    status = 1;
end
