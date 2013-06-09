function [boxes, parts, pixelscoremaps] = detect_sup(im, models, thresh_reduce)
% sanja: modified version using mygdetect

if nargin < 3
   thresh_reduce = 0;
end;

input = color(im);

nmodels = length(models);
model = mergemodels(models);
thresh = zeros(nmodels, 1);
for i = 1 : nmodels
    thresh(i) = models{i}.thresh + thresh_reduce;
end;

% get the feature pyramid
pyra = featpyramid(input, model);

[dets, boxes, info, pixelscoremaps] = mygdetect(pyra, model, min(thresh), [], 0);
if ~numel(dets), return; end;
indkeep = find(dets(:, 6) > thresh(dets(:, 5)));
dets = dets(indkeep, :);
boxes = boxes(indkeep, :);
info = info(:, :, indkeep);

if ~isempty(boxes)
  boxes = reduceboxes(model, boxes);
  [dets boxes] = clipboxes(im, dets, boxes);
  I = nms(dets, 0.5);
  parts = boxes(I,:);
  boxes = dets(I,:);
end