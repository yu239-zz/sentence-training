function LSVM_Motatt_Detection(varargin)

%===================================================================================
% Usage:
%	LSVM_Motatt_Detection('videosListFile', '~/*/*/*/videosList.txt', ...
%	     'objectModelFile', '~/*/*/*/*/box_8C4P_final.mat', ...
%	     'usingBatchMode', true, ... % default is false
%	     'batchSize', 1, ...	% default is 0 in sequential mode,
%					% batchsize is how many videos will
%					% be processed by a same thread.
%	     'imResizeRatio', 1.5, ...  % image resize ratio, default 1.5 for C-D2a
%	     'attentionResizeRatio', 2);% attention region resize ratio, 
%					% default 2 for C-D2a.
%===================================================================================



videoslistfile = 'videosList.txt';
objectmodelfile = '';
usingbatchmode = false; % default mode
batchsize = 0; % default value when usingbatchmode == false
imresizeratio = 1.5;
attentionresizeratio = 2;

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
	elseif strcmp(lower(varargin{i}), 'objectmodelfile')
		objectmodelfile = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'usingbatchmode')
		usingbatchmode = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'batchsize')
		batchsize = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'imresizeratio')
		imresizeratio = varargin{i+1};
	elseif strcmp(lower(varargin{i}), 'attentionresizeratio')
		attentionresizeratio = varargin{i+1};
	else
		display(['Error: Cannot parse the input option ''' varargin{i} '''.']);
		usage();
		return;
	end
end


% call LSVM_motatt_single_video_detection_script
% parameters for this function are (startidx, endidx, videoslistfile, objectmodelfile, imresizeratio, attentionresizeratio)
% read in videosList.txt
videosList = textread(videoslistfile, '%s', ...
			'delimiter', '\n', ...
			'bufsize', 4095*3);
videosnum = length(videosList);

if ~usingbatchmode
	% sequential mode
	startidx = 1;
	endidx = videosnum;
	command = ['./run_LSVM_motatt_single_video_detection_script.sh /aux/matlab/R2010a ' num2str(startidx) ' ' num2str(endidx) ' ' videoslistfile ' ' objectmodelfile ' ' num2str(imresizeratio) ' ' num2str(attentionresizeratio)];
	system(command);
else
	% batch mode
	if batchsize < 1 || batchsize > videosnum
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
		command = ['./run_LSVM_motatt_single_video_detection_script.sh /aux/matlab/R2010a ' num2str(startidx) ' ' num2str(endidx) ' ' videoslistfile ' ' objectmodelfile ' ' num2str(imresizeratio) ' ' num2str(attentionresizeratio)  ' &'];
		system(command);
	end
end

%========================================================
function usage()
command = 'help LSVM_Motatt_Detection';
eval(command);


