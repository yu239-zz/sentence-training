function LSVM_motatt_single_video_detection_script(startidx, endidx, videolistfile, objectmodelfile, imresizeratio, attentionresizeratio)

if strcmp('char', class(startidx))
	startidx = str2num(startidx);
end

if strcmp('char', class(endidx))
	endidx = str2num(endidx);
end

if strcmp('char', class(imresizeratio))
	imresizeratio = str2num(imresizeratio);
end

if strcmp('char', class(attentionresizeratio))
	attentionresizeratio = str2num(attentionresizeratio);
end

videosList = textread(videolistfile, '%s', ...
			'delimiter', '\n', ...
			'bufsize', 4095*3);

for i=startidx:endidx
	video = videosList{i};

	% load in trained object model
	data = load(objectmodelfile);
	model = data.model;
	note = model.note;
	cls = model.class;
	sprintf('testing: %s\n', note)

	LSVM_motatt_single_video_detection(cls, model, 'mindeye', video, imresizeratio, attentionresizeratio);
end
