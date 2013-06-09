function [matchOtoC, matched, nO, nC, diff_score, matched_nms, max_k] = compare_boxes(boxes_O, boxes_C, k, overlap_thresh)

if (nargin < 3)
   k = 12; 
end;
if (nargin < 4)
   overlap_thresh = 0.5;
end;

if (size(boxes_O, 1))
    matchOtoC = match_boxes(boxes_O, boxes_C, k, overlap_thresh);

    % compare top k boxes
    [ind] = find(matchOtoC(:, 1) > 0);
    matched = size(ind, 1) / size(boxes_O, 1);

    [nO, iO] = diff_boxes(boxes_O, 0.2);
    [nC, iC] = diff_boxes(boxes_C, 0.2);
    if (size(ind, 1))
       diff_score = mean(matchOtoC(ind, 3));
    else
        diff_score = 0;
    end;
    max_k = max(matchOtoC(:, 1));

    % compare all different detection (aggressive nms)
    matchOtoCnms = match_boxes(boxes_O(iO, :), boxes_C, nO, overlap_thresh);
    matched_nms = size(find(matchOtoCnms(:, 1) > 0), 1) / nO;
else
    matchOtoC = [];
    matched = 1;
    nO = 0;
    nC = 0;
    diff_score = 0;
    matched_nms = 1;
    max_k = 0;
end;


function matchOtoC = match_boxes(boxes_O, boxes_C, k, overlap_thresh)

lambda = 1.5;   % weighs in the importance of the difference in score
matchOtoC = zeros(min(k, size(boxes_O, 1)), 3);

for i = 1 : min(k, size(boxes_O, 1))
    box_O = boxes_O(i, [1:4, 6]);
    
    m = -100;
    m_ind = 0;
    overlap = 0;
    score_diff = inf;
    for j = 1 : min(k, size(boxes_C, 1))
    %for j = 1 : size(boxes_C, 1)
       box_C = boxes_C(j, [1:4, 6]);
       o = get_bb_overlap(box_O(:, 1:4), box_C(:,1:4));
       if (o > overlap_thresh)
          m_score = o - lambda * abs(box_O(1, 5) - box_C(1, 5));
          if (m_score > m)
              m = m_score;
              m_ind = j;
              overlap = o;
              score_diff = box_O(1, 5) - box_C(1, 5);
          end;
       end;
    end;
    matchOtoC(i, :) = [m_ind, overlap, score_diff];
end;


function [n, I] = diff_boxes(det, nms_thresh)

I = nms(det, nms_thresh);
n = length(I);

