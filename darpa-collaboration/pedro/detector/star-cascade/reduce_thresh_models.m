function reduce_thresh_models(model_folder, out_dir, thresh_diff)

if (numel(model_folder)==0)
   model_folder = '/net/upplysingaoflun/home/salvi/originalstarmodels';
   model_folder = '/net/upplysingaoflun/aux/home/fidler/cascade_training_final';
   model_folder = '/net/upplysingaoflun/aux/home/fidler/cascade_training_6boxes';
end;
if (numel(out_dir) == 0)
   out_dir = '/net/upplysingaoflun/aux/home/fidler/cascade_training_final/artificial_models';
   out_dir = '/net/upplysingaoflun/aux/home/fidler/cascade_training_6boxes/artificial_models';
end;

if (nargin < 3)
    thresh_diff = -0.1;
end;

if ~exist(out_dir, 'dir')
    mkdir(out_dir);
end;
model_files = dir(fullfile(model_folder, '*.mat'));

fprintf('Using cascade models from: %s\n', model_folder);
fprintf('Reducing thresh by %0.2f\n', -thresh_diff);
fprintf('Outputting to: %s\n', out_dir);
for i = 1 : length(model_files)
    data = load(fullfile(model_folder, model_files(i).name));
    if (isfield(data, 'csc_model'))
        csc_model = data.csc_model;
        fprintf('   model: '' %s ''\n', csc_model.class)
        csc_model = reduce_thresh(csc_model, thresh_diff);
        save(fullfile(out_dir, model_files(i).name), 'csc_model')
    end;
end;
