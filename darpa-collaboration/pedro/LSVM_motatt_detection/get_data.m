function [pos, neg] = get_data(cls, flippedpos, usepositive, DATASET_NAME, NOTE, sup, use3dbox)

% [pos, neg] = pascal_data(cls)
% Get training data from the DATASET_NAME dataset. see dataset_globals
% script for options
 
dataset_globals; % uses DATASET_NAME
if nargin < 2
  flippedpos = false;
end
if nargin < 6, sup = 'unsup'; end;
if nargin < 7, use3dbox = 0; end;

if numel(NOTE)
   cachedir = fullfile(LSVM_TRAIN_PATH, '..');
else
   cachedir = LSVM_TRAIN_PATH; 
end;

annot_path = ANNOTATION_3D_PATH;
annotationfileformat = fullfile(annot_path, '%s.mat');
annotationfileformat = strrep(annotationfileformat,'\','\\');
suffix = '';
if use3dbox == 1, suffix = '_3d'; end;
if usepositive, suffix = [suffix '_pos']; end;
train_file = fullfile(cachedir, [cls '_train_' sup suffix '.mat']);

try
  load(train_file);
catch
  % positive examples from train+val
  ids = dir(fullfile(annot_path, '*.mat'));
  pos = [];
  numpos = 0;
  for i = 1:length(ids);
    fprintf('%s: parsing positives: %d/%d\n', cls, i, length(ids));
    data = load(fullfile(annot_path, ids(i).name));
    if ~isfield(data, 'annotation'), continue; end;
    annotation = data.annotation;
    annotation.name = strrep(annotation.name, '\', '/');
    [path, imname, ext] = fileparts(annotation.name);
    if ~exist(fullfile(IMAGES_POS_PATH, [imname ext]), 'file'), continue; end;
    for j=1:length(annotation.class), if ~numel(annotation.class{j}), annotation.class{j} = ''; end; end;
    clsinds = strmatch(cls, annotation.class, 'exact');
    if isempty(clsinds), continue; end;
    % skip difficult examples
    fprintf('done\n');
    try
        diff = {annotation.difficult{clsinds}};
        diff_t = [];
        for j=1:length(annotation.class), if numel(diff{j}) & diff{j}==1, diff_t = [diff_t; 1]; end; end;
    catch, diff_t = []; end;
    clsinds(diff_t) = [];
    for j = clsinds(:)'
      numpos = numpos+1;
      pos(numpos).im = fullfile(IMAGES_POS_PATH, [imname ext]);
      bbox_t = annotation.bboxes(j, :);
      if all(bbox_t==0),
          numpos = numpos-1;
          continue;
      end;
      if ~use3dbox
         bbox_t = annotation.bboxes(j, :);
         bbox = [bbox_t(1:2), bbox_t(1:2)+bbox_t(3:4)]; % my annotations are [left,top,width,height]
      else
         basebox = annotation.basebox{j}; 
         if ~numel(basebox)
             numpos = numpos-1;
             continue;
         end;
         bbox = get2dfrom3dbox(basebox);
      end;
      bbox = round(bbox);
      bbox = [max(1, bbox(1)), max(1, bbox(2)), min(annotation.imsize(2), bbox(3)), min(annotation.imsize(1), bbox(4))];
      pos(numpos).x1 = bbox(1);
      pos(numpos).y1 = bbox(2);
      pos(numpos).x2 = bbox(3);
      pos(numpos).y2 = bbox(4);
      pos(numpos).flip = false;
      pos(numpos).trunc = annotation.truncated{j};
      haspose = 0;
      if isfield(annotation, 'pose3D') & numel(annotation.pose{j})
         pos(numpos).pose = annotation.pose3D(j, :);
         haspose = 1;
      end;
      if ~numel(pos(numpos).trunc), pos(numpos).trunc = 0; end;
      pos(numpos).obj_num = j;
      if flippedpos
        oldx1 = bbox(1);
        oldx2 = bbox(3);
        bbox(1) = annotation.imsize(2) - oldx2 + 1;
        bbox(3) = annotation.imsize(2) - oldx1 + 1;
        numpos = numpos+1;
        pos(numpos).im = pos(numpos-1).im;
        pos(numpos).x1 = bbox(1);
        pos(numpos).y1 = bbox(2);
        pos(numpos).x2 = bbox(3);
        pos(numpos).y2 = bbox(4);
        pos(numpos).flip = true;
        pos(numpos).trunc = pos(numpos-1).trunc;
        pos(numpos).obj_num = j;
        if haspose
            pos(numpos).pose = [360 - pos(numpos-1).pose(1), pos(numpos-1).pose(2)];
        end;        
      end
    end
  end

  fprintf('   found %d positives examples\n', length(pos))
  % negative examples from train (this seems enough!)
  if ~usepositive
      directoryfiles = dir(fullfile(IMAGES_NEG_PATH, '*.*'));
      ids = get_files_pattern(directoryfiles, '', IMAGES_NEG_PATH);  % filters out non-images
      neg = [];
      numneg = 0;
  else
      % use all positive examples, and filter out positive bounding boxes during detection
      neg = [];
      numneg = 0;
      imfile = '';
      flip = -1;
      fprintf('using negative crops from positive images...\n');
      for i = 1 : length(pos)
           %if ~pos(i).flip
              imfile_i = pos(i).im;
              flip_i = pos(i).flip;
              if ~strcmp(imfile, imfile_i) | flip_i~=flip
                  numneg = numneg + 1;
                  imfile = imfile_i;
                  neg(numneg).bboxes = [];
                  neg(numneg).im = pos(i).im;
                  neg(numneg).flip = pos(i).flip;
                  neg(numneg).ispositive = 1;
              end;
              neg(numneg).bboxes = [neg(numneg).bboxes; [pos(i).x1, pos(i).y1, pos(i).x2, pos(i).y2]];
           %end;
      end;
      neg = join_neg_images(neg);
      numneg = length(neg);
      directoryfiles = dir(fullfile(IMAGES_POS_PATH, '*.*'));
      ids = get_files_pattern(directoryfiles, '', IMAGES_POS_PATH);  % filters out non-images
  end;
  
  for i = 1:length(ids);
    fprintf('%s: parsing negatives: %d/%d\n', cls, i, length(ids));
    annotationfile = fullfile(annot_path, [ids(i).imname '.mat']);
    clsinds = [];
    if exist(annotationfile, 'file'), 
        data = load(annotationfile);
        if isfield(data, 'annotation')
           annotation = data.annotation;  
           for j=1:length(annotation.class), if ~numel(annotation.class{j}), annotation.class{j} = ''; end; end;
           clsinds = strmatch(cls, annotation.class, 'exact');
        end;
    end;
    if length(clsinds) == 0
      numneg = numneg+1;
      neg(numneg).im = ids(i).name;
      neg(numneg).flip = false;
      neg(numneg).ispositive = 0;
      numneg = numneg+1;
      neg(numneg).im = ids(i).name;
      neg(numneg).flip = true;
      neg(numneg).ispositive = 0;
    end
  end
  fprintf('   found %d negative examples\n', length(neg))
  
  save(train_file, 'pos', 'neg');
end  


function neg = join_neg_images(negall)

images = [];
for i = 1 : length(negall)
    imcur = negall(i).im;
    flipcur = negall(i).flip;
    ind = find_id(images, imcur, flipcur);
    if ~numel(ind)
        n = length(images) + 1;
        images(n).im = imcur;
        images(n).flip = flipcur;
    end;
end;

neg = [];
fields = fieldnames(negall);
for i = 1  : length(images)
    ind = find_id(negall, images(i).im, images(i).flip);
    for j = 1 : length(fields)
       field = fields{j};
       val = getfield(negall, {ind(1)}, field);
       neg = setfield(neg, {i}, field, val);
    end;
    for j = 2 : length(ind)
        neg(i).bboxes = [neg(i).bboxes; negall(ind(j)).bboxes];
    end;
    [u, v] = unique(neg(i).bboxes, 'rows');
    neg(i).bboxes = u;
end;


function ind = find_id(images, imname, flip)

ind = [];
for i = 1 : length(images)
    imcur = images(i).im;
    flipcur = images(i).flip;
    if strcmp(imcur, imname) & flipcur==flip
        ind = [ind; i];
    end;
end;

function bbox = get2dfrom3dbox(boxView)

vertices = boxView(1:2,:);

xmin = min(vertices(1, :));
ymin = min(vertices(2,:));
xmax = max(vertices(1,:));
ymax = max(vertices(2,:));

bbox = [xmin, ymin, xmax, ymax];
