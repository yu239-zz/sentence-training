function b = reduceboxes2(model, boxes)
% Eliminate columns for filters that are not used.
% E.g., [0 0 0 0 10 20 110 120] -> [10 20 110 120, i]
% keeps component number i too
% Index end-1 is the component label and index end is the 
% detection score.
%
% model  object model
% boxes  filter boxes returned by gdetect.m

% n = #filters per component (assuming all components have
% the same number of parts)

n = length(model.rules{model.start}(1).rhs);
% n*4+2 := 4 coordinates per boxes plus the component index 
% and score
b = zeros(size(boxes, 1), n*5+2);
maxc = max(boxes(:,end-1));
for i = 1:maxc
  % process boxes for component i
  I = find(boxes(:,end-1) == i);
  tmp = boxes(I,:);
  keep = [];
  comp = [];
  % find unused filters
  for j = 1:4:size(boxes, 2)-2
    % count # of non-zero coordinates
    s = sum(sum(tmp(:,j:j+3)~=0));
    % the filter was not used if all coordinates are zero
    if s
      keep = [keep j:j+3];
      comp = [comp, round((j-1) / 4)+1];
    end
  end
  % remove all unused filters
  ind = repmat([1 : 4], [length(comp), 1]);
  ind = ind + 5 * repmat([0 : length(comp) - 1]', [1, 4]);
  ind = reshape(ind', [1, numel(ind)]); 
  b(I,ind) = tmp(:, keep);
  b(I, [5 : 5 : length(comp) * 5]) = repmat(comp, [size(I, 1), 1]);
end
