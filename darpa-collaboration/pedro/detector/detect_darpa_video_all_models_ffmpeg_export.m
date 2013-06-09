function detect_darpa_video_all_models_ffmpeg_export(videoname, model_folder, thresh_diff, nms_thresh, model_list)

    % Runs Pedro's detector on all frames of the video, for all of the listed
    % models. The results are exported as scheme readable .sc files.
    %
    % thresh_diff is the amount added to model-threshold to get more false positives
    % nms_thresh is to control non-maximal suppression

    % ------------------------------------------ Profiling
    movie_tic = tic;
    [status, timestr] = system('date "+%F %X"');
    fprintf(1, 'Detect and export, starting %s\n', timestr);

    % ------------------------------------------ Setup Path
    addpath('~/darpa-collaboration/ideas')
    addpath('~/darpa-collaboration/pedro/detector')
    addpath('~/darpa-collaboration/pedro/detector/star-cascade')
    addpath('~/darpa-collaboration/ffmpeg/')

    % ------------------------------------------ Parse Arguments
    if (nargin < 3)
        thresh_diff = 0;
    end
    if (nargin < 4)
        nms_thresh = 0.8;
    end
    if (nargin < 5)
        model_list={};
    end

    if isempty(model_folder)
        model_folder = [getenv('HOME') '/video-datasets/C-D1a/voc4-models/'];
    end

    % Load the passed models
    models = load_models(model_folder,model_list);
    n_models = length(models);
    if n_models == 0
        fprintf(1, 'No models have been loaded, aborting\n');
        exit;
    end

    % We use the greatest padding for all models, so that we can share
    % the least number of HOG pyramids across all models for a single
    % frame.
    [padx, pady] = get_max_padxy(models);

    % Load the passed video
    [dirname, vid_basename, vid_ext] = fileparts(videoname);
    if isempty(dirname)
       dirname = [getenv('HOME') '/video-datasets/C-D1/recognition/'];
    end

    % ------------------------------------------ Execution----------- %
    % Export gdetect info for every model-frame pair in models-video
    % -- %

    % Prepare the video for reading
    ffmpegOpenVideo([ dirname '/' vid_basename vid_ext]);
    frame_no=1;

    while(not(ffmpegIsFinished()))
        % Load in the image
        im = double(ffmpegGetFrame());

        % Directory where we save results
        export_filedir = sprintf('%s/%s/%04d', dirname, vid_basename, frame_no);
        status = system(sprintf('mkdir -p "%s"', export_filedir)); % Make sure it exists
        if status ~= 0
            fprintf(1, 'Failed to create directory: %s, aborting.\n', export_filedir);
            break;
        end

        frame_tic = tic; % profile (time) each frame
        fprintf(1, 'frame %04d: ', frame_no); % user feedback all on one line
        first = 1; % first pieces of feedback, so we know when to omit a comma

        % Before generating hog pyramids, check to see if it needs to be done.
        do_gen_hog = 0;
        for n = 1:n_models
            model = models{n};
            filename = get_export_filename(model);
            if exist(filename, 'file') ~= 2
               do_gen_hog = 1;
               break;
            end
        end

        if do_gen_hog == 0
            % User feedback
            if first == 1; first = 0; else; fprintf(1, ', '); end % place comma before user feedback
            fprintf(1, 'skipping hog generation ');
        else
            % We need a hog_pyramid for every unique combination of
            % sbin, interval, padx and pady in the passed models
            hog_pyras = []; % Where we store hog pyramids for this frame
            for n = 1:n_models
                model = models{n};
                key = pyra_key(model);
                if isfield(hog_pyras, key) == 0
                    pyra_start_toc = toc(frame_tic);   % profile

                    % User feedback
                    if first == 1; first = 0; else; fprintf(1, ', '); end % place comma before user feedback
                    fprintf(1, 'hog[%d,%d] ', model.sbin, model.interval);

                    % Generate hog descriptor
                    hog_pyras.(key) = featpyramid2(im, padx, pady, model.sbin, model.interval);

                    % Print profiling inforation
                    fprintf(1, '(%0.1fs)', toc(frame_tic) - pyra_start_toc);
                end
            end
        end

        % Calculate and export detection scores
        for n = 1:n_models
            % Profile just this model
            model_start_toc = toc(frame_tic);

            % The model of interest
            model = models{n};

            % The export filename
            filename = get_export_filename(model);

            % Start user feedback on thismodel
            if first == 1; first = 0; else; fprintf(1, ', '); end
            fprintf(1, '%s ', model.filename);

            if exist(filename, 'file') == 2
                fprintf(1, 'export file exists ');
            else
                % Load the relavent hog pyramid
                pyra = hog_pyras.(pyra_key(model));

                % Score the model
                scored_model = gdetect_score_only(pyra, model, model.thresh + thresh_diff);

                % Store some pyramid information on the model
                scored_model.pyra_pad = [ pyra.pady pyra.padx ];
                scored_model.pyra_scales = pyra.scales;
                save('/tmp/scored_model.mat','scored_model','-v7.3');
                % Export the result
                sc_filename = filename(1:length(filename) - 3); % strip .gz
                export_pedro_model_to_scheme(scored_model, sc_filename);
                system(sprintf('gzip %s', sc_filename));
            end

            % Update user feedback
            elapsed = toc(frame_tic) - model_start_toc;
            fprintf(1, '(%0.1fs)', elapsed);
        end

        % Update user feedback
        if n_models > 1; fprintf(1, ' -- total of '); end
        fprintf('%0.1fs\n', toc(frame_tic));

        % Read for next frame
        frame_no = ffmpegNextFrame();
    end

    % ------------------------------------------ Cleanup
    ffmpegCloseVideo();

    % Profiling user feedback
    [status, timestr] = system('date "+%F %X"');
    fprintf('Finished at %s. Elapsed seconds = %d\n', timestr, toc(movie_tic));

    % returns all cascade models in the passed folder, that are also
    % in "model_list". If "model_list" is empty, then all models are
    % returned.
    function models = load_models(model_folder, model_list)
        model_files = dir(fullfile(model_folder, '*.mat'));
        models = cell(length(model_files),1);
        pntr = 1; % cell-index into models

        for i = 1:length(model_files)
            model_filename = model_files(i).name;

            if ~isempty(model_list)
                % If we passed a list of models, then check the current
                % model_file is in that list.
                considered_model=false;
                for j = 1:length(model_list)
                    extentionless_model_filename=model_filename(1:length(model_filename)-4); % strip .mat
                    if strcmp(extentionless_model_filename,model_list{j})
                        considered_model=true;
                        break;
                    end
                end
                if ~considered_model
                    continue; % model_file was not in "model_list"
                end
            end

            data = load(fullfile(model_folder, model_filename));

            % Warn about attempting to use csc_model files
            if (isfield(data, 'csc_model'))
                fprintf('Warning, csc_model files are not supported: %s\n', model_filename);
            end

            if isfield(data, 'model')
                [path, name, ext] = fileparts(model_filename);
                data.model.filename = name;
                models{pntr} = data.model;
                pntr = pntr + 1;
            end
        end

        % Remove empty trailing cells
        models = models(1:pntr - 1);

        % User feedback
        if isempty(model_list)
            fprintf(1, 'Found models: ');
            for i = 1:length(models)
               if i > 1; fprintf(1, ', '); end; % place comma
               fprintf(1, models{i}.filename);
            end
            fprintf('\n');
        else
            fprintf(1, 'Found models: ');
            display(intersect(cellfun(@(m) m.filename, models, 'UniformOutput', false), model_list))
            fprintf(1, 'Missing models: ');
            display(setdiff(model_list, cellfun(@(m) m.filename, models, 'UniformOutput', false)))
        end
    end

    function [key] = pyra_key(model)
        key = sprintf('sbin_%d_interval_%d', model.sbin, model.interval);
    end


    function [padx, pady] = get_max_padxy(models)
        padx = 0;
        pady = 0;
        for i = 1:length(models)
            [padx_i, pady_i] = getpadding(models{i});
            padx = max(padx, padx_i);
            pady = max(pady, pady_i);
        end
    end

    function filename = get_export_filename(model)
        filename = sprintf('%s/scores_%s.sc.gz', export_filedir, model.filename);
    end

end
