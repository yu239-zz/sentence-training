function get_opticalflow(dirname, videoname)
	addpath('~/darpa-collaboration/optical-flow/bilgic');
    d_frames = dir_regexp([dirname '/' videoname],'^[0-9]+$');
    for i =1:length(d_frames)-1
	opt_file = sprintf('%s/%s/%04d/optical-flow.ssv',dirname,videoname,i);
	opt_file2 = sprintf('%s/%s/%04d/optical-flow.ssv.gz',dirname,videoname,i);
	if (exist(opt_file) || exist(opt_file2))
	    fprintf('%03d - Using precomputed Optical-Flow..\n',i);
	    continue
	else
	    fprintf('%03d - Precomputing OpticalFlow..      \n',i);
	    I1 = impyramid( imInit(sprintf('%s/%s/%04d/frame.ppm',dirname,videoname,i)),'reduce');
	    I2 = impyramid( imInit(sprintf('%s/%s/%04d/frame.ppm',dirname,videoname,i+1)),'reduce');
	    [flowHor flowVer] = pyramidFlow(I1, I2, 5, 3, 3); %pyramidFlow( I1, I2, winSize, ITER_NO, PYRE_NO )
	    dlmwrite(opt_file,[flowHor;flowVer],'delimiter',' ');
	end
    end
    fprintf('\n');
    fprintf('Optical Flow done.');
end
