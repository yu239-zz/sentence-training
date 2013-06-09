function detect_darpa_video_frame(dirname,videoname,model_name,thresh_diff,nms_thresh,frame,hostname)
% Runs Pedro's detector on one frame of a video
% thresh_diff is the amount added to model-threshold to get
% more false positives
% nms_thresh is to control non-maximal suppression

    load(['~/video-datasets/C-D1a/voc4-models/' model_name '.mat']);
    model = bboxpred_dummy(model);

    fprintf('Detection Start.\n');
    fprintf('frame: %04d\n',frame);
    im = imread(sprintf('%s/%s/%04d/frame.ppm',dirname,videoname,frame));
    boxes = process(im,model,(model.thresh + thresh_diff),nms_thresh);
    boxes_file = sprintf('%s/%s/%04d/voc4-%s.boxes',dirname,videoname,frame,model_name);
    fid = fopen(boxes_file, 'w');
    fprintf(fid, '%ld %ld %ld %ld %ld %ld\n', boxes');
    fclose(fid);
    status = unix(sprintf('rsync -a -v -z -e "ssh -x" %s %s:%s',boxes_file,hostname,boxes_file))
    while status
        status = unix(sprintf('rsync -a -v -z -e "ssh -x" %s %s:%s',boxes_file,hostname,boxes_file))
    end
    fprintf('Pedro Detection Done\n');
end
