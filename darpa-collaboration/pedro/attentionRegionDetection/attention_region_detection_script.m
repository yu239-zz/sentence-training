function attention_region_detection_script(startidx, endidx, videoslistfile, target_threshold, background_threshold, target_area_ratio_threshold, background_area_ratio_threshold)

if strcmp('char', class(startidx))
	startidx = str2num(startidx);
end

if strcmp('char', class(endidx))
	endidx = str2num(endidx);
end

if strcmp('char', class(target_threshold))
	target_threshold = str2num(target_threshold);
end

if strcmp('char', class(background_threshold))
	background_threshold = str2num(background_threshold);
end

if strcmp('char', class(target_area_ratio_threshold))
	target_area_ratio_threshold = str2num(target_area_ratio_threshold);
end

if strcmp('char', class(background_area_ratio_threshold))
	background_area_ratio_threshold = str2num(background_area_ratio_threshold);
end

videosList = textread(videoslistfile, '%s', ...
			'delimiter', '\n', ...
			'bufsize', 4095*3);

for i=startidx:endidx
	video = videosList{i};	
	attention_region_detector(video, target_threshold, background_threshold, target_area_ratio_threshold, background_area_ratio_threshold);
end
