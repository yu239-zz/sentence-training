function video = darpa_video(name)

addpath('~/darpa-collaboration/ideas')

%video = darpa_video(['Approach1_A1_C1_Act1_4_DOWNTOWN1A3_MC_MIDD_4798658a-c5af-11df-9b88-e80688cb869a'])

%load('VOC2007/person_final')
load('person_final');

%thresh=-0.5;

%f = figure('visible','off');
model = bboxpred_dummy(model);

[dirname, name, ext] = fileparts(name);
if isempty(dirname)
    dirname = [getenv('HOME') '/video-datasets/C-D1a/SINGLE_VERB/'];
end
name = [name ext];

D = [dirname name];
Dir = dir_regexp(D,'^[0-9]+$');
S=size(Dir(:,1));
for i=1:S(1)
    i

    clf
    %    iptsetpref('ImshowBorder','tight')
    set(gcf, 'visible','off');

    im = imread([D '/' Dir(i).name '/' 'frame-full.ppm']);
%    boxes = process(im, model, thresh);
    boxes = process(im, model, -1);
    display 'show'
    showboxes(im, boxes);

    fid = fopen([D '/' Dir(i).name '/' 'voc4.boxes'], 'w');
    fprintf(fid, '%ld %ld %ld %ld %ld %ld\n', boxes');
    fclose(fid);

    axis off
    set(gca,'ytick',[]);
    set(gca,'xtick',[]);
    set(gcf, 'Units','normal')
    set(gca, 'Position',[0 0 1 1])
    [H,W,Depth] = size(im);
    dpi = 100;
    set(gcf, 'paperposition', [0 0 W/dpi H/dpi]);
    set(gcf, 'papersize', [W/dpi H/dpi]);
    print(gcf, sprintf('-r%d',8*dpi), '-dtiff', [D '/' Dir(i).name '/' 'voc4.tiff']);
end
