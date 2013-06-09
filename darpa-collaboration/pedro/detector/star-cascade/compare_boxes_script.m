
video_dir_O = '/net/perisikan/aux/zhangz/tmp-video-datasets/C-D1b/videos2/';
%video_dir_C{1,1} = '/net/arivu/aux/salvi/tmp-video-datasets/C-D1b/videos/';
%video_dir_C{2,1} = '/net/verstand/aux/zhangz/tmp-video-datasets/C-D1b/videos-starwithoutpruning/';
if ~exist('cascade', 'var')
    cascade = 1;
end;
if (cascade==1)
     video_dir_C = '/net/upplysingaoflun/aux/home/fidler/usc29/oldcascade';
     video_dir_C = '/net/perisikan/aux/waggonej/tmp-video-datasets/C-D1b/usc29/';
     %video_dir_C = '/net/upplysingaoflun/aux/waggonej/tmp-video-datasets/C-D1b/usc29/';
     video_dir_C = '~/detections/4/';
elseif (cascade == 2)
    video_dir_C = '/net/upplysingaoflun/aux/home/fidler/usc29/artificialcascade';
else
   video_dir_C = '/net/upplysingaoflun/aux/home/fidler/usc29/artificialcascade2';
end;
boxfilepattern = '*.boxes';
k_boxes = 12;
overlap_thresh = 0.4;
fprintf('Directory with original models: %s\n', video_dir_O)
fprintf('Directory with cascade models: %s\n', video_dir_C)
fprintf('   overlap thresh = %0.2f\n', overlap_thresh)
fprintf('   k boxes = %d\n', k_boxes)
[matched, nO, nC, diff_score, matched_nms, max_k] = compare_boxes_main(video_dir_O, video_dir_C, boxfilepattern, k_boxes, overlap_thresh);
save('~/matlab/compare_stats_04.mat','matched','nO','nC','diff_score','matched_nms','max_k')