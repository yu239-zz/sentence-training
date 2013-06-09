function train_DPM(model)

disp('starting');

addpath '~/darpa-collaboration/pedro/detector/';
addpath '~/darpa-collaboration/bin/';

dirname='darpa-collaboration/data/C-D1/training-data';

disp('Gathering negative samples');
createNegative(dirname,model);

disp('Creating temporary negative frames');
createImages(dirname,model,'negative')

disp('Creating Datamatrix for training');
createDataMatrix(dirname,model,'negative');

disp('Training model');
cd '~/darpa-collaboration/pedro/detector/';
pascal_train(model,3,dirname);

disp('model trained');
copyfile([getenv('HOME') '/' dirname '/' model '/temp/' model '.mat'],['/aux/qobi/video-datasets/C-D1/voc4-models-original/' model '.mat']);

end
