function showdetection(im, boxes, topK)

if nargin < 3
    topK = size(boxes, 1);
end;
imshow(im);
hold on;
for i = 1 : min(size(boxes, 1), topK)
    rectangle('position', [boxes(i, 1:2), boxes(i,3:4)-boxes(i,1:2)], 'linewidth', 2, 'edgecolor', [1,0,0]);
end;