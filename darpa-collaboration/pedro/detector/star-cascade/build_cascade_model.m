function csc_model = build_cascade_model(model, csc_dir, pca, thresh)

if (nargin < 3)
   pca = 0;
end;

if (nargin < 4)
   thresh = -0.5;
end;
disp(csc_dir);
csc_model = cascade_model_2(model, csc_dir, pca, thresh);
