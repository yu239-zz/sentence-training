function csc_model = cascade_train_main(model, csc_folder, cscmodel_file,modelname,thresh_diff, pca, pos)

% model_file ... file for the original model
% csc_folder ... folder to store out detection statistics
% cscmodel_file ... file to store the cascade model to
%addpath ./voc-release4
%addpath ../

if nargin < 6
    pca = 8;
end

fprintf('Using %d principal components\n', pca);

disp(csc_folder);
model=bboxpred_dummy(model);
disp(model.class);
inffile = fullfile(csc_folder, [model.class '_cascade_data_det.inf']);
if exist(inffile, 'file')
    unix(['rm ' inffile]);
end;
cascade_train(model, csc_folder, modelname, pos, 0);
fprintf('Using %d principal components\n', pca);
pcafile = fullfile(csc_folder, [model.class '_cascade_data_pca' num2str(pca) '.inf']);
if exist(pcafile, 'file')
    unix(['rm ' pcafile]);
end;
cascade_train(model, csc_folder,modelname, pos, pca)
csc_model = build_cascade_model(model, csc_folder, pca, model.thresh + thresh_diff);
save(cscmodel_file, 'csc_model');
