function driver_text(dirname,model,framejump,videonumber,framenumber)

%%%%%%%%%%%%%%%%%% create frames from videos in the folder %%%%%%%%%%%%%%
% createFrames(dirname,numofframes);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%% create positive samples  %%%%%%%%%%%%%%%%%%%%%%%%%%%%%
get_contour_manually_new(dirname,model,framejump,videonumber,framenumber,'positives');
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%% create negative samples %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

getNegativeContour(dirname,model);
% get_contour_manually_new(dirname,model,framejump,videonumber,framenumber,'negatives');
% createNegative(dirname,model);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%





end