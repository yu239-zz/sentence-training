DATASET_NAME = 'mrsc';
dataset_globals;

classes={'body','sheep','sign','aeroplane',...
         'cow', 'bicycle','car','sign',...
         'face', 'cat','flowers','tree','boat',...
         'flower','bird', 'book','chair','dog'};
numP=[2,2,2,4,4,4,6,6,6,8,8,8]';
numC=2*[2,3,4,1,2,3,1,2,3,1,2,3]';
whichtest = 'test';
suffix = 'upscale2';
outdir = fullfile(IMAGES_PATH, '..', 'LSVM-det-results', suffix);

for i = 1 : length(classes)
   cls = classes{i};
   plotperformance_components(cls, DATASET_NAME, numC, numP, whichtest, '', outdir)
end;