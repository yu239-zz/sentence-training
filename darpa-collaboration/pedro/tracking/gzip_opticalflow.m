function get_opticalflow(dirname, videoname)
    d_frames = dir_regexp([dirname '/' videoname],'^[0-9]+$');
    for i =1:length(d_frames)-1
	opt_file = sprintf('%s/%s/%04d/optical-flow.ssv',dirname,videoname,i);
	if (exist(opt_file))
	    fprintf('%03d - Gzipping Optical-Flow..\n',i);
		unix(['gzip ' opt_file ]);
	    continue
	end
    end
    fprintf('\n');
end
