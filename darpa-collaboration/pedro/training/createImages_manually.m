function [ output_args ] = createImages_manually(dirname,model,negativedir)
%UNTITLED2 Summary of this function goes here
%   Detailed explanation goes here



filenames=textread([getenv('HOME') dirname '/' model '-negatives.text'],'%s','delimiter','\n');

numofsamples=length(filenames);
temp=textread([getenv('HOME') dirname '/' model '-negatives.text'],'%s');



for i=1:numofsamples
    disp(['frame:' num2str(i)]);
    foldername=temp{(6*(i-1)+1),1};
    frame=temp{(6*(i-1)+2),1};
    mkdir(negativedir,foldername);
    mkdir([negativedir '/' foldername],frame);
    
    
    y1=str2num(temp{(6*(i-1)+3),1});
    x1=str2num(temp{(6*(i-1)+4),1});
    y2=str2num(temp{(6*(i-1)+5),1});
    x2=str2num(temp{(6*(i-1)+6),1});
    
    im=imread([getenv('HOME') '/video-datasets/C-D1b/videos/' foldername '/' frame '/frame.ppm']);
    negative=im(x1:x2,y1:y2);
    imwrite(negative,[negativedir '/' foldername '/' frame '/frame-negative.ppm'],'ppm');





end

end
