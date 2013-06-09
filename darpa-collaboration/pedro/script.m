%demonstrates all of the steps to create a Felzenswalb model from a set of
%videos
%matlab must be started using darpa-wrap for this script to function

%% Setup
clear

tic

addpath ~/darpa-collaboration/ffmpeg/
addpath ~/darpa-collaboration/pedro/detector
globals %this script sets several global variables


%%
% matlabpool 3 %start up the parallel toolbox for speed (optional)

%% Work for standard Felzenswalb model

cd ~/darpa-collaboration/pedro/training

dirname = ['/home/' getenv('USERNAME') '/darpa-collaboration/data/C-D1/person-basic/'];
framedirname = ['/aux/' getenv('USERNAME') '/tmp-video-datasets/C-D1/recognition/'];
matfile = ['/home/' getenv('USERNAME') '/darpa-collaboration/data/C-D1/person-basic/person-basic.mat'];
model = 'person-basic';
cls = 'person-basic-1-2';
corpus = 'C-D1/recognition';

delete([dirname model '-negatives.text'])
delete([dirname '*.mat'])

if strcmpi(input(['Delete ' tmpdir '? (y/n) '], 's'), 'y')
    unix(['rm -r ' tmpdir]);
end
if strcmpi(input(['Delete ' cachedir '? (y/n) '], 's'), 'y')
    unix(['rm -r ' cachedir]);
end
if strcmpi(input(['Delete ' [dirname '/temp'] '? (y/n) '], 's'), 'y')
    unix(['rm -r ' dirname '/temp']);
end


get_contour_manually_new(dirname, corpus, model, 1, 'positives', 50)
%notes on the automatic negative generation:
%   =>if modelname-negatives.text exists, this function will append new training data to its end
%   =>This function generates twice as many pieces of training data as were present in the modelname-positives.text file.
createNegative(dirname, model) 

createImages(dirname,model,framedirname)
createDataMatrix(dirname,model,framedirname)
cd ~/darpa-collaboration/pedro/detector
training_pipeline(corpus, matfile, cls, 1, dirname, 2, '')

%% Star-cascade model
%the star-cascade model generation relies on a standard Felzenswalb model
%which it uses as a starting point
clear
addpath('~/darpa-collaboration/pedro/detector/star-cascade')
framedirname = ['/aux/' getenv('USERNAME') '/tmp-video-datasets/C-D1/recognition/'];
cd ~/darpa-collaboration/pedro/detector/
model_dir = ['/tmp/' getenv('USERNAME') '/annotations/'];
dir_result = ['/tmp/' getenv('USERNAME') '/'];
% cscmodel_file = ['/tmp/' getenv('USERNAME') '/person.mat'];
cscmodel_file = '/home/rbuffin/darpa-collaboration/data/C-D1/person-basic/person-basic.mat'

% must exist /tmp/rbuffin/annotations/person-cascade-positives.txt
% same as positives from star step
createDataMatrixCascade(framedirname, model_dir, dir_result, 'person-basic')
cascade_train_main('/tmp/person.mat',dir_result,cscmodel_file,'person-basic',0)

toc
