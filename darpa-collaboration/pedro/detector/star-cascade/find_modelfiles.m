function [files] = find_modelfiles(originalmodel_dir)

% find all models for original detectors (cascade training will work with
% this models)
%-----------------------------------------------------------------
files=dir(fullfile(originalmodel_dir, '*.mat'));

if isempty(files)
    modelformat = ['%s/%s_final.mat'];
    files_all=dir(fullfile(originalmodel_dir, '*'));
    files = struct('name', {});
    pntr = 1;
    for i = 1 : length(files_all)
        [path, modelname, ext] = fileparts(files_all(i).name);
        if exist(fullfile(originalmodel_dir, sprintf(modelformat, modelname, modelname)), 'file')
            files(pntr).name = files_all(i).name;
            pntr = pntr + 1;
        end;
    end;
end;
%-----------------------------------------------------------------