function [boxes, parts, pixelscoremaps, data_nonms] = detect(im, model, thresh, fullimname)
% sanja: modified version using mygdetect

if nargin < 3
   thresh = model.thresh;
end;

input = color(im);

% get the feature pyramid
if ~isfield(model, 'useextrafeat') | model.useextrafeat == 0
   pyra = featpyramid(input, model);
else
   [impath, imname, ext] = fileparts(fullimname);
   pyra = featpyramid_morefeat(input, model, impath, imname, 0); 
end;
%pyra = featpyramid(input, model);

boxes = [];
parts = [];
data_nonms = [];
[dets, boxes, info, pixelscoremaps] = mygdetect(pyra, model, thresh, [], 0);
if ~isempty(boxes)
  boxes = reduceboxes(model, boxes);
  [dets boxes] = clipboxes(im, dets, boxes);
  data_nonms.boxes = dets;
  data_nonms.parts = boxes;
  I = nms(dets, 0.5);
  parts = boxes(I,:);
  boxes = dets(I,:);
end