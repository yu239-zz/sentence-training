function scored_models = pedro_score_frame(frame_path,models,thresh_diff,padx,pady,im)
% ffmpeg has been started before, and an image loaded
% all relevant models have been loaded in
% max padding also has been computed
% this function just returns the scored model(s)
    hog_pathname = [frame_path 'hog-pyramid.mat'];
    if exist(hog_pathname, 'file') ~= 2
        hog_pyramids = [];
        for i = 1:length(models)
            model = models{i}.model;
            hog_pyramids{i} = featpyramid2(im,padx,pady,model.sbin,model.interval);
        end
        save(hog_pathname,'hog_pyramids','-v7.3');
    else
        fprintf(2, 'Using precomputed HOG pyramid\n');
        load(hog_pathname);
    end
    scored_models = [];
    for i = 1:length(models)
        model = models{i}.model;
        pyramid = hog_pyramids{i};
        scored_models{i} = gdetect_score_only(pyramid,model,model.thresh+thresh_diff);
        scored_models{i}.pyra_pad = [pyramid.pady pyramid.padx];
        scored_models{i}.pyra_scales = pyramid.scales;
    end
    clear model pyramid hog_pyramids;
end
