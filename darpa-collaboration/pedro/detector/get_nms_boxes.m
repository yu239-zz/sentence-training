function [det, all] = get_nms_boxes(im, model, det, all, nms_thresh)

try
    if ~isempty(det)
      try
        % attempt to use bounding box prediction, if available
        bboxpred = model.bboxpred;
        [det all] = clipboxes(im, det, all);
        [det all] = bboxpred_get(bboxpred, det, reduceboxes(model, all));
      catch
      end
      [det all] = clipboxes(im, det, all);
      I = nms(det, nms_thresh);
      det = det(I,:);
      all = all(I,:);
    end
catch
end;