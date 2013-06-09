function run_converter(infile, outfile)
%converts a _final.mat model to the cascade version and saves it to a
%libcudafelz binary model.
%
% Example: run_converter('VOC2007/car_final.mat', 'exported_car_final');

addpath('star-cascade/');
mc = load(infile);
model = mc.model;
pca = 5;
csc_model = cascade_model(model, model.year, pca, -0.5);
csc_model.export_file = outfile;
pyra = featpyramid(ones(16,16,3), csc_model);
[dCSC, bCSC] = cascade_detect(pyra, csc_model, csc_model.thresh);
