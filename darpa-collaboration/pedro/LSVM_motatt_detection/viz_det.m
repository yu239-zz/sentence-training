function h = viz_det(im, segfile)

% im can be either image or imagefile
% segfile can either be a file or lsvmdata

try
    im = imread(im);
end;
try
   load(segfile, 'lsvmdata')
catch
    lsvmdata = segfile;
end;
[classes, gtcols] = getclassinfo();
gtcols = gtcols/255;

s = 0;
if isfield(lsvmdata, 'segs')
    for i = 1 : length(lsvmdata)
       for j = 1 : length(lsvmdata(i).segs)
          s = max(s, numel(lsvmdata(i).segs{j}));
       end;
    end
end;

seg_thresh = 0.0;
f = 1.5;
if ~s, h=[]; return; end;
if ~s
    h = figure('position', [150,150,size(im,2)*f, size(im,1)*f]);
    %im(:,:,1)=double(im(:,:,1))*0.4+0.6*255*double(seg_i>0.15*max(seg_i(:)));
    subplot('position', [0,0,1,1]); imagesc(uint8(im)); axis equal; axis off;
    seg = plot_det(im, lsvmdata, gtcols, 0, seg_thresh);
else
    h = figure('position', [150,150,size(im,2)*2*f, size(im,1)*f]);
    %im(:,:,1)=double(im(:,:,1))*0.4+0.6*255*double(seg_i>0.15*max(seg_i(:)));
    subplot('position', [0,0,0.5,1]); imagesc(uint8(im)); axis equal; axis off;
    seg = plot_det(im, lsvmdata, gtcols, 1, seg_thresh);
    subplot('position', [0.5,0.,0.5,1]); imshow(uint8(round(seg))); axis equal; axis off;
end;


function seg = plot_det(im, lsvmdata, gtcols, plotseg, seg_thresh)

    seg = zeros(size(im));
    scoremap = zeros(size(im, 1), size(im, 2));
    for j = 1 : length(lsvmdata)
        boxes = lsvmdata(j).boxes;
        cls = lsvmdata(j).class;
        if plotseg
           segs = lsvmdata(j).segs;
        end;
        d = dist2(gtcols(j, :), [0,0,0; 1,1,1]);
        if d(1) < d(2), col = [1,1,1]; else col = [0,0,0]; end;
        for i = 1 : size(boxes, 1)
           rectangle('position',[boxes(i,1:2),boxes(i,3:4)-boxes(i,1:2)],'EdgeColor',gtcols(j, :)*0.8,'Linewidth',3)
           text(boxes(i, 1)+4, boxes(i, 2)+10, cls, 'BackgroundColor',gtcols(j, :),'Fontsize',10,'fontweight','bold','Color',col);
           if plotseg
               scoretemp = segs{i} * boxes(i, 5);
               scoremap = max(scoremap, scoretemp);
               ind = find((scoremap == scoretemp) & (scoretemp > seg_thresh * max(scoremap(:))));
               for k = 1 : 3
                   temp = seg(:, :, k);
                   temp(ind) = gtcols(j, k)*255 * segs{i}(ind)/max(segs{i}(:));
                   seg(:,:,k) = temp;
               end;
           end;
        end;
    end;