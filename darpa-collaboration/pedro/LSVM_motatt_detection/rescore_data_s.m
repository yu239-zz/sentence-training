function [boxes, parts, X] = rescore_data_s(im_dir, data_dir, imlist, classes, ind_classes)

% Compute feature vectors for context rescoring.

numids = length(imlist);
numcls = length(ind_classes);

% get dimensions of each image in the dataset
sizesfiles = fullfile(data_dir, 'models', 'sizes.mat');
try
  load(sizesfiles)
catch
  sizes = cell(numids,1);
  for i = 1:numids;
    name = fullfile(im_dir, imlist{i});
    im = imread(name);
    sizes{i} = size(im);
  end
  save(sizesfiles, 'sizes');
end

for j = 1 : numcls
    k = ind_classes(j);
    cls = classes(k).class;
    boxesfile = fullfile(data_dir, 'models', [cls '_boxes.mat']);
    try
        load(boxesfile);
    catch
        boxes = cell(numids, 1);
        parts = cell(numids, 1);
        for i = 1:numids
            [path, imname, ext] = fileparts(imlist{i});
            lsvmfile = fullfile(data_dir, [imname '.mat']);
            data = load(lsvmfile);
            lsvmdata = data.lsvmdata;
            boxes{i} = lsvmdata(k).boxes;
            parts{i} = lsvmdata(k).parts;
        end
        save(boxesfile, 'boxes', 'parts');
    end;
end;

% generate the rescoring data
rescorefile = fullfile(data_dir, 'models', 'rescore_data.mat');
try
  load(rescorefile);
catch
  boxes = cell(numcls, 1);
  parts = cell(numcls, 1);
  models = cell(numcls, 1);
  for i = 1:numcls
    k = ind_classes(i);
    cls = classes(k).class;
    load(fullfile(data_dir, 'models', [cls '_final']));
    models{i} = model;
    boxesfile = fullfile(data_dir, 'models', [cls '_boxes.mat']);
    data = load(boxesfile);
    boxes{i} = data.boxes;
  end
  
  for j = 1:numcls
    data = cell2mat(boxes{j});
    % keep only highest scoring detections
    if size(data,1) > 50000
      s = data(:,end);
      s = sort(s);
      v = s(end-50000+1);
      for i = 1:numids;    
        if ~isempty(boxes{j}{i})
          I = find(boxes{j}{i}(:,end) >= v);
          boxes{j}{i} = boxes{j}{i}(I,:);
        end
      end
    end
  end
    
  % build data
  X = cell(numcls, numids);
  maxes = zeros(1, numcls);
  for i = 1:numids
    for j = 1:numcls
      if isempty(boxes{j}{i})
        maxes(j) = models{j}.thresh;
      else
        %maxes(j) = max(models{j}.thresh, max(boxes{j}{i}(:,end)));
        maxes(j) = max(max(-1, models{j}.thresh-1), max(boxes{j}{i}(:,end)));
      end
    end
    maxes = 1 ./ (1 + exp(-1.5*maxes));
    
    s = sizes{i};    
    base = [zeros(1,5) maxes];
    for j = 1:numcls
      bbox = boxes{j}{i};        
      if ~isempty(bbox) 
        n = size(bbox,1);
        x = repmat(base, [n, 1]);
        score = bbox(:,end);
        x(:,1) = 1 ./ (1 + exp(-1.5*score));
        x(:,2:5) = boxes{j}{i}(:,1:4);
        x(:,2) = x(:,2) / s(2);
        x(:,3) = x(:,3) / s(1);
        x(:,4) = x(:,4) / s(2);
        x(:,5) = x(:,5) / s(1);        
        X{j,i} = x;
      end
    end
    
  end

  save(rescorefile, 'X', ...
       'boxes', 'parts');  
end
