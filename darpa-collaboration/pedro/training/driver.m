function driver(dirname,model,negativedir,framejump)

%%%%%%%%%%%%%%%%%% create frames from videos in the folder %%%%%%%%%%%%%%
% createFrames(dirname,numofframes);    
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%% create positive samples  %%%%%%%%%%%%%%%%%%%%%%%%%%%%%
get_contour_manually_new(dirname,model,framejump);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%% create negative samples %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

getNegativeContour(dirname,model);
% createNegative(dirname,model);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%% create datamatrix for training %%%%%%%%%%%%%%%%%%%%%%%%%%%%
createDataMatrix(dirname,model,negativedir);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


%%%%%%%%%%%%%%% create model using pedro's code %%%%%%%%%%%%%%%%%%%%%%%%%
%%Note:will need to edit pascal_train.m and global.m in pedro's folder%%%
% pascal_train('motorbike',3);
%%model files stored in folder specified in global.m%%%%%%%%%%%%%%%%%%%%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


end