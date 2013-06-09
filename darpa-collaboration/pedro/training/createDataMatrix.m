function createDataMatrix(dirname,model,framedirname)

disp('Creating Data Matrix');

[dirname '/' model '-positives.text']
[dirname '/' model '-negatives.text']

filenames=textread([dirname '/' model '-positives.text'],'%s','delimiter','\n');
negfiles=textread([dirname '/' model '-negatives.text'],'%s','delimiter','\n');

pos(length(filenames))=struct;
neg(length(negfiles))=struct;

temp=textread([dirname '/' model '-positives.text'],'%s');
temp2=textread([dirname '/' model '-negatives.text'],'%s');

temp{(6*(2-1)+1),1}

try
    ffmpegCloseVideo()
end

numpos=1;
for i=1:length(pos)
    disp('positive samples');
    disp(num2str(i));
    savename=[];
    
    savename=[framedirname '/' temp{(6*(i-1)+1),1} '/' num2str(temp{(6*(i-1)+2),1}) '/frame.ppm' ];

    pos(numpos).im=savename;
    pos(numpos).video = add_video_extension([framedirname '/' temp{(6*(i-1)+1),1}]);
    pos(numpos).frame = 1;
    
    im = imreadx(pos(numpos));
    s = size(im);
    width = s(2);
    height = s(1);
    
    pos(numpos).frame = str2num(temp{(6*(i-1)+2),1});
    pos(numpos).y1=str2num(temp{(6*(i-1)+3),1});
    pos(numpos).x1=str2num(temp{(6*(i-1)+4),1});
    pos(numpos).y2=str2num(temp{(6*(i-1)+5),1});
    pos(numpos).x2=str2num(temp{(6*(i-1)+6),1});
    pos(numpos).flip=false;
    pos(numpos).image = [];
    
    pos(numpos+1).video = pos(numpos).video;
    pos(numpos+1).frame = pos(numpos).frame;
    pos(numpos+1).y1=pos(numpos).y1;
    pos(numpos+1).x1=width-pos(numpos).x2+1;
    pos(numpos+1).y2=pos(numpos).y2;
    pos(numpos+1).x2=width-pos(numpos).x1+1;
    pos(numpos+1).flip=true;
    pos(numpos+1).image = [];

    numpos=numpos+2;
end


for i=1:length(neg)
    disp('negative samples');
    disp(num2str(i));
    negativesavename=[framedirname '/' temp2{(7*(i-1)+1),1} '/' num2str(temp2{(7*(i-1)+2),1}) '/frame-negative-' num2str(temp2{(7*(i-1)+3),1}) '.ppm'];
    neg(i).im=negativesavename;
    neg(i).flip=false;
    neg(i).video = add_video_extension([framedirname '/' temp2{(7*(i-1)+1),1}]);
    neg(i).frame = str2num(temp2{(7*(i-1)+2),1});
    neg(i).image = [];
end

save([dirname '/' model '.mat'],'pos','neg');

end

