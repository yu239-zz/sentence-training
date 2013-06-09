function [det, all] = process(image, model, thresh, nms_thresh)

% bbox = process(image, model, thresh)
% Detect objects that score above a threshold, return bonding boxes.
% If the threshold is not included we use the one in the model.
% This should lead to high-recall but low precision.

% siddharth: exposed the nms threshold
%            higher value[0,1] => less suppression

globals;

if nargin < 4
  nms_thresh = 0.5
end

if nargin < 3
  thresh = model.thresh
end

[det, all] = imgdetect(image, model, thresh);

if ~isempty(det)
  try
    % attempt to use bounding box prediction, if available
    bboxpred = model.bboxpred;
    [det all] = clipboxes(image, det, all);
    [det all] = bboxpred_get(bboxpred, det, reduceboxes(model, all));
  catch
  end
  [det all] = clipboxes(image, det, all);
  I = nms(det, nms_thresh);
  det = det(I,:);
  all = all(I,:);
end
