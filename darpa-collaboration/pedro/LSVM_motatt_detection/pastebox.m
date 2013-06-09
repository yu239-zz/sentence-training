function [segim, detbox, maskbox] = pastebox(segim, detbox, mask)

% FUNCTION pastes the expected segmentation mask ('mask') into a detection
% box ('detbox') in segmentation map ('segim')
% INPUT
% segim ... is a segmentation map(will be 1 for an object and 0 everywhere else) and is of image size
%           note that segim does not need to be empty, the mask will be
%           inserted into it with the max operation
% detbox ... is a box output by a detector and is of format
%            [x1,y1,x2,y2,...], where (x1,y1) is top-left and (x2,y2) bottom-right
%            coordinate
% mask ... is the expected segmentation mask of the object

% OUTPUT
% segim ... segmentation map, where the mask has been pasted into detbox
%           (clipping on the border is handled)
% detbox ... clipped detbox (if it was on the border)
% maskbox ... mask warped to fit detbox


segbox = imresize(mask, detbox(1,[4,3])-detbox(1,[2,1])+1, 'bicubic');
x1 = 1; y1 = 1; x2 = size(segbox, 2); y2 = size(segbox, 1);
if detbox(1) < 1, x1 = x1 - detbox(1) + 1; detbox(1) = 1;  end;
if detbox(2) < 1, y1 = y1 - detbox(2) + 1; detbox(2) = 1;  end;
if detbox(3) > size(segim, 2), x2 = x2 + size(segim, 2)- detbox(3);  detbox(3) = size(segim, 2); end;
if detbox(4) > size(segim, 1), y2 = y2 + size(segim, 1)- detbox(4);  detbox(4) = size(segim, 1); end;

maskbox = segbox(y1:y2,x1:x2);
segim(detbox(1,2):detbox(1,4),detbox(1,1):detbox(1,3)) = max(segim(detbox(1,2):detbox(1,4),detbox(1,1):detbox(1,3)), maskbox);