function [im,ex] = imreadx(ex)

% Read a training example image.
%
% ex  an example returned by pascal_data.m
% returns an updated ex that caches the image

persistent lastex;
if isempty(lastex)
    lastex.video = '';
    lastex.frame = -1;
end

ex = fillExampleVideo(ex);

if isfield(ex, 'image') && not(isempty(ex.image))
    im = ex.image;
else
    if isfield(ex, 'video')
        if exist(ex.video, 'file') == 2
            im = color(ffmpegGetFrameNfast(lastex.video, lastex.frame, ...
                                           ex.video, ex.frame, 0));
            lastex = ex;
        else
            fprintf(2, 'Failed to find/open movie file "%s"\n', ex.video);
            error('File not found');
        end
    else
        im = color(imread(ex.im)); 
    end
    if isfield(ex, 'flip')
        if ex.flip
            im = im(:,end:-1:1,:);
        end
    end
    ex.image = im;
end
