function Attention_Region_Detection(varargin)

%=========================================================================================
% Usage:
%	Attention_Region_Detection('videosListFile', '~/*/*/*.txt', ...
%				   'usingBatchMode', true, ... % default is false
%				   'batchSize', 1, ... % default is 0, assume not 
%						       % using batch mode
%				   'targetThreshold', 0.15, ... % default value
%				   'backgroundThreshold', 0.08, ... % default value
%				   'targetAreaRatioThreshold', 0.002, ... % default value
%				   'backgroundAreaRatioThreshold', 0.4); % default value
%=========================================================================================


target_threshold = 0.15;
background_threshold = 0.08;
target_area_ratio_threshold = 0.002;
background_area_ratio_threshold = 0.4;

videoslistfile = 'videosList.txt';
usingbatchmode = false; % default mode
batchsize = 0; % default value when usingbatchmode == false

% video, target_threshold, background_threshold, target_area_ratio_threshold, background_area_ratio_threshold

varnum = length(varargin);
% check if input variables
if mod(varnum, 2)~=0 || varnum==0
	display('Error: Input parameters number errors.');
	usage();
	return;
end

for i=1:2:varnum
	if strcmp(lower(varargin{i}), 'videoslistfile')
		videoslistfile = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'usingbatchmode')
		usingbatchmode = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'batchsize')
		batchsize = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'targetthreshold')
		target_threshold = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'backgroundthreshold')
		background_threshold = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'targetarearatiothreshold')
		target_area_ratio_threshold = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'backgroundarearatiothreshold')
		background_area_ratio_threshold = varargin{i+1};
	else
		display(['Error: Cannot parse the input option ''' varargin{i} '''.']);
		usage();
		return;
	end
end

% call attention_region_detection_script
videosList = textread(videoslistfile, '%s', ...
			'delimiter', '\n', ...
			'bufsize', 4095*3);
videosnum = length(videosList);

if ~usingbatchmode
	% sequential mode
	startidx = 1;
	endidx = videosnum;
	command = ['darpa-wrap ./run_attention_region_detection_script.sh /aux/matlab/R2010a ' num2str(startidx) ' ' num2str(endidx) ' ' videoslistfile ' ' num2str(target_threshold) ' ' num2str(background_threshold) ' ' num2str(target_area_ratio_threshold) ' ' num2str(background_area_ratio_threshold)];
	system(command);
else
	% batch mode
	if batchsize<1 || batchsize>videosnum
		error('Error: The batchsize must be larger than 1 and less than the number of testing videos.');
		return;
	end
	threadnumber = ceil(videosnum/double(batchsize));
	for i=1:threadnumber
		startidx = (i-1)*batchsize+1;
		endidx = i*batchsize;
		if endidx > videosnum
			endidx = videosnum;
		end
		command = ['darpa-wrap ./run_attention_region_detection_script.sh /aux/matlab/R2010a ' num2str(startidx) ' ' num2str(endidx) ' ' videoslistfile ' ' num2str(target_threshold) ' ' num2str(background_threshold) ' ' num2str(target_area_ratio_threshold) ' ' num2str(background_area_ratio_threshold) ' &'];
		system(command);
	end
end


%==========================================================
function usage()
command = ['help Attention_Region_Detection'];
eval(command)
