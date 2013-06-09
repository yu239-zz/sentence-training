function rescore_train_s(data_dir, DATASET_NAME, ind_classes)

% Train context rescoring SVMs.
addpath('C:\science\code\LSVM_modified\svm_601mex\matlab')
addpath('C:\science\code\LSVM_modified\svm_601mex\bin')
if nargin < 1
    DATASET_NAME = 'mrsc';
end;
if nargin < 3
    ind_classes = load(fullfile(data_dir, 'classinfo.txt'));
end;

dataset_globals;
im_dir = ALLPATHS.IMAGES_PATH;
annot_path = ALLPATHS.ANNOTATION_PATH;

train_file = fullfile(ALLPATHS.DATA_PATH, 'Train.txt');
imlist = textread(train_file, '%s');
imlist_name = getimnames(imlist); % extract just the image name form the image list

[classes, gtcols] = getclassinfo();
if numel(IGNORE_CLASSES)
   classes = filterclasses(classes, IGNORE_CLASSES);   % classes
   gtcols(IGNORE_CLASSES,:) = [];
end;
numcls = length(classes);

[boxes, parts, XX] = rescore_data_s(im_dir, data_dir, imlist, classes, ind_classes);

% train classifiers
for i = 1:numcls
  k = ind_classes(i);
  cls = classes(k).class;
  fprintf('\nTraining rescoring classifier: %s (%d/%d)\n', cls, i, numcls);
  rescorefile = fullfile(data_dir, 'models', [cls '_rescore_classifier.mat']);
  try
    load(rescorefile);
  catch
    rescorelabelsfile = fullfile(data_dir, 'models', 'rescore_labels.mat');
    YY = rescore_labels_s(cls, boxes{i}, imlist_name, annot_path, rescorelabelsfile);
    %YY = rescore_labels(cls, boxes{i}, dataset);
    X = [];
    Y = [];
    for j = 1:size(XX,2)
      X = [X; XX{i,j}];
      Y = [Y; YY{j}];
    end
    I = find(Y == 0);
    Y(I) = [];
    X(I,:) = [];
    model = svmlearn(X, Y, ...
       '-t 1 -d 3 -r 1.0 -s 1.0 -j 2 -c 1.0 -e 0.001 -n 5 -m 500');    
    save(rescorefile, 'model');
  end
end