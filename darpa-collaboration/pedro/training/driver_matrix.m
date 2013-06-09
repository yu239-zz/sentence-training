function driver_matrix(dirname,model,negativedir)



%%%%%%%%%%%%% create datamatrix for training %%%%%%%%%%%%%%%%%%%%%%%%%%%%
 createDataMatrix(dirname,model,negativedir);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


%%%%%%%%%%%%%%% create model using pedro's code %%%%%%%%%%%%%%%%%%%%%%%%%
%%Note:will need to edit pascal_train.m and global.m in pedro's folder%%%
% pascal_train('motorbike',3);
%%model files stored in folder specified in global.m%%%%%%%%%%%%%%%%%%%%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


end