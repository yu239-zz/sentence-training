function extractRequiredFrames(directory, model, corpus)

numberOfSamples = length(textread([getenv('HOME') '/' directory '/' model '-positives.text'],'%s','delimiter','\n'));
data = textread([getenv('HOME') '/' directory '/' model '-positives.text'],'%s');

for i=1:numberOfSamples
    disp(['frame:' num2str(i)]);
    videoName = data{(6*(i-1)+1),1};
    frameNumber = data{(6*(i-1)+2),1};
    mkdir([getenv('HOME') '/video-datasets/' corpus '/' videoName '/'], frameNumber);

    im=imread([getenv('HOME') '/video-datasets/' corpus '/' videoName '/' frame '/frame.ppm']);
    croppedIm = im(str2num(data{(6*(i-1)+3),1}):str2num(data{(6*(i-1)+5),1}),str2num(data{(6*(i-1)+4),1}):str2num(data{(6*(i-1)+6),1}));
    imwrite(negative,[getenv('HOME') '/' directory '/' negativedir '/' foldername '/' frame '/frame-pedro-negative.ppm'],'ppm');
    '/frame-pedro-negative-' model '.ppm'
end
