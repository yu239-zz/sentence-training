function I = ffmpegGetFrameNfast(lastvideo,lastn,video,n,flipped)
% Returns the Nth frame from video
% if the last read video was the same, and if the last read frame was less
% than the current one, then it avoids reoping the video and starting from
% the front.
% this allows consecutive frame reads to be done in constant time, but
% still allows non-consequtive reads without any extra effort on the part
% of the calling function
% It also flips the image horizontally if flipped is true
I=-1;
if (strcmp(lastvideo,video) && lastn == n)
    try 
        I = ffmpegGetFrame();
    catch
        ffmpegGetFrameNfast('',-1,video,n,flipped);
    end
elseif (strcmp(lastvideo,video) && lastn < n)
    try 
        frame=lastn;
        while(not(ffmpegIsFinished()))
            if (frame == n)
                I = ffmpegGetFrame();
                break
            end
            frame = ffmpegNextFrame();
        end
    catch
        ffmpegGetFrameNfast('',-1,video,n,flipped);
    end
else
    try
        ffmpegCloseVideo();
    catch
    end
    ffmpegOpenVideo(video);
    frame = 1;
    while(not(ffmpegIsFinished()))
        if (frame == n)
            I = ffmpegGetFrame();
            break
        end
        frame = ffmpegNextFrame();
    end
    
end

if flipped
    I = I(:,end:-1:1,:);
end
