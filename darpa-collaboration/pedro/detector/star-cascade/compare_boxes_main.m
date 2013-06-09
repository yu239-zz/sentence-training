function [matched, nO, nC, diff_score, matched_nms, max_k] = compare_boxes_main(video_dir_O, video_dir_C, boxfilepattern, k_boxes, overlap_thresh)

if (nargin < 3)
   boxfilepattern = '*.boxes';
end;
if (nargin < 4)
    k_boxes = 12;
end;

if (size(video_dir_C, 1) == 1)
    temp = video_dir_C;
    clear('video_dir_C');
    video_dir_C{1} = temp;
end;

files = dir(video_dir_O);
fprintf('%d videos found\n', length(files) - 2)
matched = 0;
nO = 0;
nC = 0;
diff_score = 0;
matched_nms = 0;
sO = 0;
sC = 0;
max_k = 0;
n = 0;
n_m = 0;


for j = 1 : length(files)
    video_name = fullfile(video_dir_O, files(j).name);
    e = 0;
    for c = 1 : size(video_dir_C, 1)
       video_name2 = fullfile(video_dir_C{c}, files(j).name);
       if exist(video_name2, 'dir')
           e = 1;
       end;
    end;
    if ((isfolder(video_name)) & (e))
        fprintf('Getting stats for %s\n', files(j).name)
        frames = dir(video_name);
        fprintf('   num of frames: %d\n', length(frames) - 2);

        for k = 1 : length(frames)
            frame_dir = fullfile(video_name, frames(k).name);
            if (isfolder(frame_dir))
                if (mod(k - 1, 50) == 0)
                    fprintf('   frame %d\n', k)
                end;
                boxes_files = dir(fullfile(frame_dir, boxfilepattern));
                for i = 1 : length(boxes_files)
                   boxes_O = load(fullfile(frame_dir, boxes_files(i).name));
                   boxes_C = [];
                   e = 0;
                   for c = 1 : size(video_dir_C, 1)
                       try
                          boxes_C = load(fullfile(video_dir_C{c}, files(j).name, frames(k).name, boxes_files(i).name));
                          e = 1;
                       end;
                   end
                   if (e)
                   [matchOtoC, matched_j, nO_j, nC_j, diff_score_j, matched_nms_j,max_k_j] = compare_boxes(boxes_O, boxes_C, k_boxes, overlap_thresh);
                   if (size(matchOtoC, 1))
                       matched = (matched *n + matched_j) / (n + 1);
                       nO = (nO *n + nO_j) / (n + 1);
                       nC = (nC *n + nC_j) / (n + 1);
                       if (matched_j > 0)
                          diff_score = (diff_score *n_m + diff_score_j) / (n_m + 1);
                          max_k = (max_k *  n_m + max_k_j) / (n_m + 1);
                          n_m = n_m + 1;
                       end;
                       matched_nms = (matched_nms * n + matched_nms_j) / (n + 1);
                       sO = (sO *n + size(boxes_O, 1)) / (n + 1);
                       sC = (sC *n + size(boxes_C, 1)) / (n + 1);
                       n = n + 1;
                   end;
                   end;
                end;
            end;
        end;
        matched
        matched_nms
        max_k
        diff_score
        sO
        sC
        nO
        nC
    end;
end;


