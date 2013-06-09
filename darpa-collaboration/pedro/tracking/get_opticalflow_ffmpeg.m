function get_opticalflow_ffmpeg(videoFilename,dirname)
%calculates the optical flow between pairs of images in the video
%then calculates the corresponding integral images
%results are saved as .flo files inside of a zip file
%useage: get_opticalflow_ffmpeg(videoFilename,dirname)
addpath('~/darpa-collaboration/ffmpeg');
addpath('~/darpa-collaboration/optical-flow/bilgic');
addpath('~/darpa-collaboration/pedro/detector');
ffmpegOpenVideo(videoFilename);
frame = 1;
prevFrame = [];
nextFrame = im2double(rgb2gray(ffmpegGetFrame()));
while(not(ffmpegIsFinished()))
    frame = ffmpegNextFrame();
    prevFrame = nextFrame;
    nextFrame = im2double(rgb2gray(ffmpegGetFrame()));
    if (zip_archive_has_file([dirname,'.zip'], sprintf('%06d.flo', frame)))
        fprintf('Using precomputed Optical-Flow...\n');
        continue
    else
        I1 = impyramid(prevFrame,'reduce');
        I2 = impyramid(nextFrame,'reduce');
        [flowHor flowVer] = pyramidFlow(I1, I2, 5, 3, 3);
        data = cat(3, intImg(flowHor), intImg(flowVer)); %compute the integral images
        flo_to_zip(dirname, sprintf('%06d.flo', frame-1), data); 
    end
end
ffmpegCloseVideo();
