function LSVM_motatt_single_video_detection(cls, model, DATASET_NAME, video, imresizeratio, attentionresizeratio)

[status HOME] = system('echo $HOME');
HOME = HOME(1:end-1);

% determine video_path
% 	read in darpa-corpora.text
darpa_corpora = textread([HOME '/darpa-collaboration/documentation/darpa-corpora.text'], '%s', ...
			'bufsize', 4095*10);
%	find path for video

video_path = [];

for i=2:2:length(darpa_corpora)
	if strcmp(darpa_corpora{i}, video)
		video_path = darpa_corpora{i-1};
		video_path = [HOME '/video-datasets/' video_path];
		break;
	end
end

if length(video_path)==0
	error(['Error: Cannot find ''' video ''' in the darpa-corpora.text ']);
	return;
end

if ~iscell(model)
	note = model.note;
else
	note = model{1}.note;
end


out_path = fullfile(video_path, video, 'LSVM-results', note);
out_path_im = fullfile(video_path, video, 'LSVM-results', note, 'imdata');

if ~exist(out_path, 'dir'), mkdir(out_path); end;
if ~exist(out_path_im, 'dir'), mkdir(out_path_im); end;

boxes_file = fullfile(out_path, [cls '_boxes']);

directoryfiles = dir(fullfile(video_path, video, 'images', ['*.*']));
ids = get_files_pattern(directoryfiles, '', fullfile(video_path, video, 'images')); % filters out non-images

boxes = cell(length(ids), 1);
parts = cell(length(ids), 1);
data_nonms = cell(length(ids), 1);

attentionBoxes = textread([video_path '/' video '/attentions/attentions.txt'], '%s', ...
				'delimiter', '\n', ...
				'bufsize', 4095*20);

batchsize = 4;
num = ceil(length(ids) / batchsize)

% run detector in each attention regions on each frame
for k = 1:num
	pardata = struct('boxes', {}, 'parts', {}, 'scoremaps', {}, 'imname', {}, 'imsize', {}, 'data_nonms', {});
	% compute data in parallel batches
	for j = 1:batchsize % parfor
		i = (k-1) * batchsize + j;
		if (i <= length(ids))
			fileout = fullfile(out_path_im, [ids(i).imname '.mat']);
			fprintf('class (%s), dataset (''%s''), model(%s), testing: image (%d/%d)\n', cls, DATASET_NAME, note, i, length(ids))
			im = imread(ids(i).name);
			originalimsize = size(im);
			if imresizeratio ~= 1
				im = imresize(im, imresizeratio, 'bicubic');
			end
			imsize = size(im);

			framename = ids(i).name;
			dashpositions = find(framename=='-');
			dashposition = dashpositions(end);
			frameidx = str2num(framename(dashposition+1:end-4));	


			line = attentionBoxes{frameidx*2};
			line = regexp(line, ' ', 'split');
			aboxnum = length(line)/4;

			parts_i = [];
			boxes_i = [];

			for kk = 1:aboxnum
				x1 = str2num(line{(kk-1)*4+1});
				y1 = str2num(line{(kk-1)*4+2});
				x2 = str2num(line{(kk-1)*4+3});
				y2 = str2num(line{kk*4});
		
				if x1==0 && y1==0 && x2==0 && y2==0
					break;
				end

				% resize the attention box to 'imresizeratio' times
				x1 = floor(x1*imresizeratio);
				y1 = floor(y1*imresizeratio);
				x2 = floor(x2*imresizeratio);
				y2 = floor(y2*imresizeratio);

				attHeight = y2-y1+1;
				attWidth = x2-x1+1;

				% double the attention box region
				xmean = (x1+x2)/2;
				ymean = (y1+y2)/2;

				x1 = floor(xmean - attWidth/2*attentionresizeratio);
				y1 = floor(ymean - attHeight/2*attentionresizeratio);
				x2 = floor(xmean + attWidth/2*attentionresizeratio);
				y2 = floor(ymean + attHeight/2*attentionresizeratio);

				if x1<1
					x1 = 1;
				end
				if y1<1
					y1 = 1;
				end
				if x2>imsize(2)
					x2 = imsize(2);
				end
				if y2>imsize(1)
					y2 = imsize(1);
				end

				sub_img = im(y1:y2, x1:x2, :);

				[boxes_i_temp, parts_i_temp, scoremaps_i_temp, data_nonms_i_temp] = detect(sub_img, model, model.thresh-1, ids(i).name);

				if length(boxes_i_temp) ~= 0
					% recover the boxes position in the original size image
					boxes_i_temp(:,1) = (boxes_i_temp(:,1)+x1-1)/imresizeratio;
					boxes_i_temp(:,2) = (boxes_i_temp(:,2)+y1-1)/imresizeratio;
					boxes_i_temp(:,3) = (boxes_i_temp(:,3)+x1-1)/imresizeratio;
					boxes_i_temp(:,4) = (boxes_i_temp(:,4)+y1-1)/imresizeratio;
					
					for mm=1:(size(parts_i_temp, 2)-2)/4
					parts_i_temp(:,(mm-1)*4+1) = (parts_i_temp(:,(mm-1)*4+1)+x1-1)/imresizeratio;
					parts_i_temp(:,(mm-1)*4+2) = (parts_i_temp(:,(mm-1)*4+2)+y1-1)/imresizeratio;
					parts_i_temp(:,(mm-1)*4+3) = (parts_i_temp(:,(mm-1)*4+3)+x1-1)/imresizeratio;
					parts_i_temp(:, mm*4) = (parts_i_temp(:, mm*4)+y1-1)/imresizeratio;
					end
			
					% put all boxes together
					parts_i = cat(1, parts_i, parts_i_temp);
					boxes_i = cat(1, boxes_i, boxes_i_temp);
				end
			end

			pardata(j).parts = parts_i;
			pardata(j).boxes = boxes_i;
			pardata(j).scoremaps = []; % scoremaps_i_temp;
			% pardata(j).data_nonms = data_nonms_i_temp;
			pardata(j).imname = ids(i).imname;
			pardata(j).imsize = originalimsize;
		end
	end

	% write batch to disk
	for j = 1:batchsize
		i = (k-1) * batchsize + j;
		if (i <= length(ids))
			fileout = fullfile(out_path_im, [pardata(j).imname '.mat']);

			% sort results descendly on likelihood
			if length(pardata(j).boxes)~=0
				boxes_temp = pardata(j).boxes;
				parts_temp = pardata(j).parts;

				boxes_temp_new = boxes_temp;
				parts_temp_new = parts_temp;

				likelihood = boxes_temp(:, 6);
				[Y, I] = sort(likelihood, 'descend');
				for kk=1:length(I)
					boxes_temp_new(kk, :) = boxes_temp(I(kk), :);
					parts_temp_new(kk, :) = parts_temp(I(kk), :);
				end

				pardata(j).boxes = boxes_temp_new;
				pardata(j).parts = parts_temp_new;
			end

			lsvmdata = pardata(j);
			save(fileout, 'lsvmdata');
			boxes{i} = pardata(j).boxes;
			parts{i} = pardata(j).parts;
			
			% data_nonms{i} = pardata(j).data_nonms;
		end
	end
end
save(boxes_file, 'boxes', 'parts'); 


