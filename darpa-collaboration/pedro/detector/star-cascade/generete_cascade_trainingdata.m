function generete_cascade_trainingdata(data_dir, data_file, output_file, model, thresh_diff, nms_thresh, extend_factor, max_per_frame)

% function works by running original model on larger image area around the
% training boxes (by factor 'extend_factor' in each of the 4 directions of the box) 
% and takes boxes ('max_per frame' number of them) that are higher than model.thresh +
% VARIABLES:
% thresh_diff for training
% generates training samples for cascade detector with reduced threshold
% data_dir ... root folder, where video directories are
% data_file ... file with annotation
% output_file ... file to store the new boxes too
% model ... original model (either loaded model, or a file containing the model)
% thresh_diff ...  will take all boxes that pass threshold of model.thresh + thresh_diff
% nms_thresh ... performs nms after detection
% extend_factor ... extends the original training box by this factor in
% each of the 4 directions
% max_per_frame ... max boxes to take per each frame (samples this number
% out of all detected boxes). note that original box will be automatically
% added, so there will be max_per_frame + 1 boxes per original annotation

if (nargin < 5)
    thresh_diff = -1;
end;
if (nargin < 6)
    nms_thresh = 0.8;
end;
if (nargin < 7)
   extend_factor = 0.6;
end;
if (nargin < 8)
   max_per_frame = 4;
end;
%if isfile('model')
if ~isfield(model, 'class')
    % check if input is actually just a file containing the model, isfile
    % doesn't work with purdue's matlab, so a bit of hacking...
    load(model, 'model');
end;

fid = fopen(data_file);
C = textscan(fid, '%s %s %d %d %d %d');
fclose(fid);



n = size(C{1}, 1);

fid = fopen(output_file, 'w+');

for i = 1 : n
    videoname = C{1}{i};
    frame = C{2}{i};
    x1 = C{3}(i, :); y1 = C{4}(i, :); x2 = C{5}(i, :); y2 = C{6}(i, :);
    bbox_or = [x1, y1, x2, y2];
    bbox_or = double(bbox_or);
    y1 = C{3}(i, :); x1 = C{4}(i, :); y2 = C{5}(i, :); x2 = C{6}(i, :);
    bbox = [max(1, round(x1 - abs(x2 - x1) * extend_factor)), max(1, round(y1 - abs(y2 - y1) * extend_factor)), x2 + round(x2 + abs(x2 - x1) * extend_factor), y2 + round(y2 + abs(y2 - y1) * extend_factor)];
    bbox = double(bbox);
    imfile = sprintf('%s/%s/%s/frame.ppm',data_dir,videoname,frame);
    fprintf(['Processing annotation: %s/%s\n  box: ' repmat('%0.2f ', [1, size(bbox_or, 2)]) '\n'], videoname, frame, bbox_or)
    im = [];
    try
        im = imread(imfile);
        im = im(bbox(1, 2) : min(size(im, 1), bbox(1, 4)), bbox(1, 1) : min(size(im, 2), bbox(1, 3)), :);
    catch err,
        if ~exist(imfile, 'file')
            fprintf('image %s doesnt exist!\n', imfile);
        else
           fprintf('error loading image: %s\n', imfile);
        end;
    end;
    boxes = [];
    try
        if (numel(im))
        fprintf('Running detector... ')
        tic;
        boxes = process(im, model, model.thresh + thresh_diff,nms_thresh);    % run original detector on theimage in the box
        e = toc;
        fprintf('[%0.4f sec]\n', e);
        end;
    catch err,
        fprintf('something wrong with detector...\n');
    end;
    if ~numel(boxes)
        fprintf('No detections found!!!\n')
    else
        boxes(:, 1) = boxes(:, 1) + bbox(1, 1) - 1; 
        boxes(:, 3) = boxes(:, 3) + bbox(1, 1) - 1;
        boxes(:, 2) = boxes(:, 2) + bbox(1, 2) - 1; boxes(:, 4) = boxes(:, 4) + bbox(1, 2) - 1;
        if (size(boxes, 1) > max_per_frame)
            r = randperm(size(boxes, 1));
            p = r(1 : max_per_frame);
            boxes = boxes(p, :);
        end;
        if (numel(boxes))
            boxes = boxes(:, 1 : 4);
        end;
    end;
    
    if numel(boxes)
       boxes = boxes(:, [2, 1, 4, 3]);
    end;
    boxes = [boxes; bbox_or];
    fprintf('New boxes:\n');
    fprintf([repmat('%0.2f ', [1, size(boxes, 2)]) '\n'], boxes')

    for j = 1 : size(boxes, 1)
        fprintf(fid, '%s %s %0.0f %0.0f %0.0f %0.0f\n', videoname, frame, boxes(j, 1), boxes(j, 2), boxes(j, 3), boxes(j, 4));
    end;
end;

fclose(fid);

