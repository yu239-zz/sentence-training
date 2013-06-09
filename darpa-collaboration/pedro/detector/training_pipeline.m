function model = training_pipeline(corpus, input, cls, n, outputDirectory, parts, note)

% model = training_pipeline('C-D1/recognition', [getenv('HOME') '/darpa-collaboration/data/C-D1/recognition/person/person.mat'], 'person-3-12', 3, '/aux/abarbu/training/person-3-12/', 12, '')

% model = pascal_train(cls, n, note)
% Train a model with 2*n components using the PASCAL dataset.
% note allows you to save a note with the trained model
% example: note = 'testing FRHOG (FRobnicated HOG)

% At every "checkpoint" in the training process we reset the
% RNG's seed to a fixed value so that experimental results are
% reproducible.
initrand();
global savedir;
savedir = outputDirectory;

if nargin < 4
    note = '';
end

load(input);

globals_pipeline;
spos = split(cls, pos, n);

cachesize = 24000;
maxneg = 10;

% train root filters using warped positives & random negatives
try
    load([cachedir cls '_lrsplit1']);
catch
    initrand();
    for i = 1:n
        % split data into two groups: left vs. right facing instances
        models{i} = initmodel(cls, spos{i}, note, 'N');
        inds = lrsplit(models{i}, spos{i}, i);
        inds=sort(inds);
        [models{i},spos{i}(inds),neg] = train(cls, models{i}, spos{i}(inds), neg, i, 1, 1, 1, ...
            cachesize, true, 0.7, false, ['lrsplit1_' num2str(i)]);
    end
    save([cachedir cls '_lrsplit1'], 'models');
end

% train root left vs. right facing root filters using latent detections
% and hard negatives
try
    load([cachedir cls '_lrsplit2']);
catch
    initrand();
    for i = 1:n
        models{i} = lrmodel(models{i});
        [models{i},spos{i},neg(1:maxneg)] = train(cls, models{i}, spos{i}, neg(1:maxneg), 0, 0, 4, 3, ...
            cachesize, true, 0.7, false, ['lrsplit2_' num2str(i)]);
    end
    save([cachedir cls '_lrsplit2'], 'models');
end

pos = [];
for i = 1:1:size(spos)
    pos = [pos, spos{i}];
end

% merge models and train using latent detections & hard negatives
try
    load([cachedir cls '_mix']);
catch
    initrand();
    model = mergemodels(models);
    [model, pos, neg] = train(cls, model, pos, neg(1:maxneg), 0, 0, 1, 5, ...
        cachesize, true, 0.7, false, 'mix');
    save([cachedir cls '_mix'], 'model');
end

% add parts and update models using latent detections & hard negatives.
try
    load([cachedir cls '_parts']);
catch
    initrand();
    for i = 1:2:2*n
        model = model_addparts(model, model.start, i, i, parts, [6 6]);
    end
    [model, pos, neg(1:maxneg)] = train(cls, model, pos, neg(1:maxneg), 0, 0, parts, 10, ...
        cachesize, true, 0.7, false, 'parts_1');
    [model, pos, neg] = train(cls, model, pos, neg, 0, 0, 1, 5, ...
        cachesize, true, 0.7, true, 'parts_2');
    save([cachedir cls '_parts'], 'model');
end

save([cachedir cls '_final'], 'model');
save([outputDirectory cls], 'model');

try
    globals
    rmdir(tmpdir, 's')
    rmdir(cachedir, 's')
catch
    disp(['unable to remove tempdir and/or cachedir: ',tmpdir,' ',cachedir])
end
