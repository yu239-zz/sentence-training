function [boxes] = cropbox2(im, boxes, padding, crop)

% pads boxes
% edited by sanja

if (nargin < 4)
    crop = 1;
end;

padx = padding*(boxes(:, 3)-boxes(:,1)+1);
pady = padding*(boxes(:, 4)-boxes(:,2)+1);
x1 = round(boxes(:,1) - padx);
y1 = round(boxes(:,2) - pady);
x2 = round(boxes(:,3) + padx);
y2 = round(boxes(:,4) + pady);
if crop
   x1 = max(1, x1); y1 = max(1, y1);
   x2 = min(size(im, 2), x2);
   y2 = min(size(im, 1), y2);
end;
boxes = [x1, y1, x2, y2];
