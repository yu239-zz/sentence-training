addpath('~/darpa-collaboration/pedro/detector');
load ~/video-datasets/C-D1a/voc4-models/person_final.mat
D=dir('0*');
nr = size(D);
for i=1:nr;
    cd([D(i).name '/results-closure'])
    C=dir('frame-cropped-person-1_solution_*.jpg');
    I=imread('../frame-cropped-person-1.ppm');
    nr = size(C);
    for j=1:nr;
	C(j).name
	T=bsxfun(@times, I, uint8(im2bw(imread(C(j).name))));
	[CDir,CFile,CExt] = fileparts([C(j).name]);
	imwrite(T,[CFile '_masked.jpeg']);
	dlmwrite([CFile '.voc4'], process(T, model, -5), ' ');
    end
    cd ../..
end
