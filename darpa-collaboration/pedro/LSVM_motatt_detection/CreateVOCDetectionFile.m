function CreateVOCDetectionFile(varargin)

%===============================================================================
%
%	CreateVOCDetectionFile('videosListFile', '/home/*/*/videosList.txt', ...
%				'objectModelFile', '/home/*/*/*.mat');
%
%===============================================================================

[status HOME] = system('echo $HOME');
HOME = HOME(1:end-1);

if mod(length(varargin), 2) ~=0 || length(varargin)==0
	display('Error: Input parameters error.');
	usage();
	return;
end

videoslistfile = 'videosList.txt';
objectmodelfile = '';

for i=1:2:length(varargin)
	if strcmp('videoslistfile', lower(varargin{i}))
		videoslistfile = varargin{i+1};
	elseif strcmp('objectmodelfile', lower(varargin{i}))
		objectmodelfile = varargin{i+1};
	else
		error(['Error: Cannot parse the input option ''' varargin{i} '''.']);
		usage();
		return;
	end
end

% read in videosList.txt
videosList = textread(videoslistfile, '%s', ...
			'delimiter', '\n', ...
			'bufsize', 4095*3);

darpa_corpora = textread([HOME '/darpa-collaboration/documentation/darpa-corpora.text'], '%s', ...
			'bufsize', 4095*3);

a = load(objectmodelfile);
note = a.model.note;
cls = a.model.class;

for i=1:length(videosList)
	video = videosList{i}
	video_path = [];
	for j=2:2:length(darpa_corpora)
		if strcmp(darpa_corpora{j}, video)
			video_path = darpa_corpora{j-1};
			break;
		end	
	end

	if length(video_path)==0
		display(['Error: Cannot find ''' video ''' in the darpa-corpora.text.']);
		return;
	end

	columnnum = 0;
	a = load([HOME '/video-datasets/' video_path '/' video '/LSVM-results/' note '/' cls '_boxes.mat' ]);
	for j=1:length(a.parts)
		parts = a.parts{j};
		s = size(parts);
		if s(1)~=0 && s(2)~=0
			columnnum = s(2);
			break;
		end
	end

	fid = fopen([HOME '/video-datasets/' video_path '/' video '/voc4-' cls '-box.boxes'], 'w');
	for j=1:length(a.parts)
		parts = a.parts{j};

		if length(parts)==0
			parts = zeros(1, columnnum-2);
			parts = [parts 1 -1e4];
		end

		fprintf(fid, '%d\n', size(parts,1));
		for k=1:size(parts,1)
			for m=1:size(parts, 2)
				fprintf(fid, '%f ', parts(k,m));
			end
			fprintf(fid, '%d %s\n', 0, cls);
		end
	end
	fclose(fid);
end


%============================================================================
function usage()
command = 'help CreateVOCDetectionFile';
eval(command);


