function [ output_args ] = createFrames(dirname,numofframes)
%UNTITLED Summary of this function goes here
%   Detailed explanation goes here





filenames=textread([dirname '/filenames.txt'],'%s');


for i=1:length(filenames)
    
    
    str1=filenames{i,1};   
    
    %%creating folder for video
    foldername=str1(1:length(str1)-4);
    mkdir(dirname,foldername);
    
    %%extracting frames for video
    str2=[' -frames ' num2str(numofframes) ' -vo jpeg '];
    str3=[dirname '/' str1 str2];
    unix(['mplayer ' str3]);    
    
    
    %%moving frames into videoname folder
    str4=[dirname '/' foldername '/'];
    unix(['mv *.jpg ' str4]); 
 


end



 

end

