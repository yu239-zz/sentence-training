function driver_main(dirname,model,framejump,videonumber,framenumber,ismanual,numsamp)

disp('starting');

addpath '~/darpa-collaboration/pedro/detector/';
addpath '~/darpa-collaboration/bin/';

%%%%%%%%%%%%%%%%%% create frames from videos in the folder %%%%%%%%%%%%%%
%str=[getenv('HOME') '/darpa-collaboration/bin/expand-video-list ' getenv('HOME') '/' dirname '/' model '.text b'];
%disp(str);
%system(str);   
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%% create positive samples  %%%%%%%%%%%%%%%%%%%%%%%%%%%%%
get_contour_manually_new(dirname,model,framejump,videonumber,framenumber,'positives',numsamp);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%% create negative samples %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
disp('Gathering negative samples');
if ismanual==0
createNegative(dirname,model);
else
getNegativeContour(dirname,model);
end

createImages(dirname,model,'negative')


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%% create datamatrix for training %%%%%%%%%%%%%%%%%%%%%%%%%%%%
createDataMatrix(dirname,model,'negative');
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%






%%%%%%%%%%%%%%%%%%%%delete frames at the end of training%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

end
