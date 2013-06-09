function [ output_args ] = createImages(dirname,model,framedirname)

[dirname '/' model '-negatives.text']
filenames=textread([dirname '/' model '-negatives.text'],'%s','delimiter','\n');

numofsamples=length(filenames);
temp=textread([dirname '/' model '-negatives.text'],'%s');

for i=1:numofsamples
    disp(['index:' num2str(i)]);
    video=temp{(7*(i-1)+1),1};
    frame=temp{(7*(i-1)+2),1};
    index=temp{(7*(i-1)+3),1};
    x1=str2num(temp{(7*(i-1)+4),1});
    y1=str2num(temp{(7*(i-1)+5),1});
    x2=str2num(temp{(7*(i-1)+6),1});
    y2=str2num(temp{(7*(i-1)+7),1});
    
%     im=imread([framedirname '/' video '/' frame '/frame.ppm']); %old method - requires expaning everyting to ppms
    im = ffmpegGetFrameN(add_video_extension([framedirname '/' video]),str2num(frame));
    negative=im(x1:x2,y1:y2);
    mkdir([framedirname '/' video '/' frame]);
    imwrite(negative,[framedirname '/' video '/' frame '/frame-negative-' index '.ppm'],'ppm');

end

end
