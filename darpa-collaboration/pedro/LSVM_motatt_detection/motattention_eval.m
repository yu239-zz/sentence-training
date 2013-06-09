function [recall] = motattention_eval(cls)

% boxes1 = LSVM_test(cls, model, testset, year, suffix)
% Compute bounding boxes in a test set.
% boxes1 are detection windows and scores.

% Now we also save the locations of each filter for rescoring
% parts1 gives the locations for the detections in boxes1
% (these are saved in the cache file, but not returned by the function)

DATASET_NAME = 'mindseye';
dataset_globals; % uses DATASET_NAME
attdir = 'C:\science\data\mindseye\C-D2a\AttentionRegion_txt';
annotfileformat = fullfile(ANNOTATION_PATH, '%s_%s.mat');
annotfileformat = strrep(annotfileformat, '\', '/');
outdir = fullfile(ANNOTATION_PATH, '..', 'Attention');
if ~exist(outdir, 'dir'), mkdir(outdir); end;
motattfileformat = fullfile(outdir, '%s_%s.mat');
motattfileformat = strrep(motattfileformat, '\', '/');
files = dir(fullfile(ANNOTATION_PATH, '*.mat'));

videolistfile = fullfile(ANNOTATION_PATH, 'videolist.mat');
if exist(videolistfile, 'file')
   load(videolistfile, 'videonames')
else
   videonames = getvideonames(ANNOTATION_PATH);
   save(videolistfile, 'videonames');
end;

% write out the attention results in a directory
if (0)
for i = 1 : length(videonames)
    videoname = videonames{i};
    fprintf('parsing video %s (%d/%d)\n', videoname, i, length(videonames));
    parsemotattfile(fullfile(attdir, [videoname '.txt']), annotfileformat, motattfileformat)
end;
end;

% evaluate
nboxes = 0;
ncorr = 0;
for i = 1 : length(files)
    data = load(fullfile(ANNOTATION_PATH, files(i).name));
    if isfield(data, 'annotation')
        annotation = data.annotation;
        if ~numel(annotation.class), continue; end;
        annotation = fixclass(annotation);
        clsinds = strmatch(cls, annotation.class, 'exact');
        if ~numel(clsinds), continue; end;
        [path, name, ext] = fileparts(files(i).name);
        motattfile = fullfile(outdir, [name '.mat']);
        bboxes = annotation.bboxes;
        bboxes = round([bboxes(:,1:2), bboxes(:,1:2)+bboxes(:,3:4)]);
        data = load(motattfile);
        attbboxes = data.attention.bboxes;
        nboxes = nboxes + length(clsinds);
        if ~numel(attbboxes), continue; end;
        %attbboxes = attbboxes(:, [2,1,4,3]);
        for k = 1 : length(clsinds)
           j = clsinds(k); 
           o = boxoverlap(attbboxes, bboxes(j, :));
           if sum(o) > 0.5
               ncorr = ncorr + 1;
           else
               aa=1;
           end;
        end;
    end;
end;
recall = ncorr / nboxes;
fprintf('attention recall: %d / %d  -> %0.4f\n', ncorr, nboxes, recall)


function videonames = getvideonames(annot_path)

files = dir(fullfile(annot_path, '*.mat'));
videonames = [];

for i = 1 : length(files)
    name = files(i).name;
    [path, name, ext] = fileparts(name);
    p = findstr(name, '_');
    if numel(p)
       p = p(end);
       videoname = name(1, 1:p - 1);
       j = findvideo(videonames, videoname);
       if ~j
           videonames{end+1} = videoname;
       end;
    end;
end;

function j = findvideo(videonames, name)

j = 0;
for i = 1 : length(videonames)
    if strcmp(videonames{i}, name)
        j = i;
    end;
end;

function parsemotattfile(filename, annotfileformat, motattfileformat)

[path, videoname, ext] = fileparts(filename);
fid = fopen(filename, 'r+');
if fid < 0, 
    aa=1;
    return;
end;
tline = fgetl(fid);
n = 1;
framenum = tline;
while ischar(tline)
    if mod(n, 2)
        if str2num(tline(1)) == 0
           framenum = tline(2:end);
        else
            break; 
        end;
    else
        annotfile = sprintf(annotfileformat, videoname, framenum);
        outfile = sprintf(motattfileformat, videoname, framenum);
        p = dir(annotfile);
        %p2 = dir(outfile);
        if numel(p)% & ~numel(p2)
            attention = [];
            tline = str2num(tline);
            attention.bboxes = reshape(tline, [4, length(tline)/4])';
            s = all(attention.bboxes == 0, 2);
            [ind] = find(s == 0);
            attention.bboxes = attention.bboxes(ind, :);
            save(outfile, 'attention')
        end;
    end;
    tline = fgetl(fid);
    n = n + 1;
end
fclose(fid);

function o = boxoverlap(a, b)

% Compute the symmetric intersection over union overlap between a set of
% bounding boxes in a and a single bounding box in b.
%
% a  a matrix where each row specifies a bounding box
% b  a single bounding box

x1 = max(a(:,1), b(1));
y1 = max(a(:,2), b(2));
x2 = min(a(:,3), b(3));
y2 = min(a(:,4), b(4));

w = x2-x1+1;
h = y2-y1+1;
inter = w.*h;
%aarea = (a(:,3)-a(:,1)+1) .* (a(:,4)-a(:,2)+1);
barea = (b(3)-b(1)+1) * (b(4)-b(2)+1);
% intersection over union overlap
%o = inter ./ (barea);
o = inter / (barea);
% set invalid entries to 0 overlap
o(w <= 0) = 0;
o(h <= 0) = 0;


function an = fixclass(an)

for i = 1 : length(an.class)
    if ~numel(an.class{i})
        an.class{i} = '';
    end;
end;