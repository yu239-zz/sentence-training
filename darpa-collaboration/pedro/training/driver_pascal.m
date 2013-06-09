function driver_pascal(dirname,model)

disp('starting pipeline');

%%%%%%%%%%%%%%% create model using pedro's code %%%%%%%%%%%%%%%%%%%%%%%%%
cd '~/darpa-collaboration/pedro/detector/';
pascal_train_pipeline(model,3,dirname);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

disp('model trained');
end
