% Set up global variables used throughout the code

% setup svm mex for context rescoring (if it's installed)
if exist('./svm_mex601') > 0
  addpath svm_mex601/bin;
  addpath svm_mex601/matlab;
end

% dataset to use
global VOCyear;
if exist('setVOCyear', 'var')
  VOCyear = setVOCyear;
  clear('setVOCyear');
elseif exist('VOCyear', 'var')
    
else
  VOCyear = '2007';
end

VOC_ROOT = 'C:\science\data\VOC\VOCdevkit\';
% directory for caching models, intermediate data, and results
cachedir = fullfile(VOC_ROOT, ['VOC' VOCyear], 'LSVM');

if exist(cachedir, 'dir') == 0
  % mkdir(fullfile(cachedir, 'learnlog'))
end

% directory for LARGE temporary files created during training
tmpdir = fullfile(VOC_ROOT, ['VOC' VOCyear], 'LSVM');

if exist(tmpdir, 'dir') == 0
  %mkdir(tmpdir); 
end

% should the tmpdir be cleaned after training a model?
cleantmpdir = true;

% directory with PASCAL VOC development kit and dataset
VOCdevkit = fullfile(VOC_ROOT, ['VOC' VOCyear], 'VOCdevkit');
mpath = matlabpath;
if ~numel(findstr(lower(mpath), lower(VOCdevkit))) & exist(VOCdevkit, 'dir'), addpath(VOCdevkit); end;