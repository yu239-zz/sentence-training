function [det, all] = cascade_image(imfile, csc_model, thresh, nms_thresh)

if (nargin < 3)
    thresh = csc_model.thresh;
end;
% imfile ... image file
% csc_model ... cascade model

im = imread(imfile);
pyra = featpyramid(double(im), csc_model);

[det, all] = cascade_detect(pyra, csc_model, thresh);
try
    if ~isempty(det)
      try
        % attempt to use bounding box prediction, if available
        bboxpred = csc_model.bboxpred;
        [det all] = clipboxes(im, det, all);
        [det all] = bboxpred_get(bboxpred, det, reduceboxes(csc_model, all));
      catch
      end
      [det all] = clipboxes(im, det, all);
      I = nms(det, nms_thresh);
      det = det(I,:);
      all = all(I,:);
    end
catch
end;



function b = getboxes(model, image, det, all)
b = [];
if ~isempty(det)
  try
    % attempt to use bounding box prediction, if available
    bboxpred = model.bboxpred;
    [det all] = clipboxes(image, det, all);
    [det all] = bboxpred_get(bboxpred, det, all);
  catch
    warning('no bounding box predictor found');
  end
  [det all] = clipboxes(image, det, all);
  I = nms(det, 0.5);
  det = det(I,:);
  all = all(I,:);
  b = [det(:,1:4) all];
end

function write_boxes(imfile, box_info)

[path, name, ext] = fileparts(imfile);
dlmwrite(fullfile(path, [name '_cascade_model.boxes']), box_info, ' ');