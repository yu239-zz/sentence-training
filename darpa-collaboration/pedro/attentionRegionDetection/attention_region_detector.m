function attention_region_detector(video, target_threshold, background_threshold, target_area_ratio_threshold, background_area_ratio_threshold)

Th1 = target_threshold;
Th2 = background_threshold;
TH_tar = target_area_ratio_threshold;
TH_bar = background_area_ratio_threshold;

[status HOME] = system('echo $HOME');
HOME = HOME(1:end-1);

% read in darpa-corpora.text
darpa_corpora = textread([HOME '/darpa-collaboration/documentation/darpa-corpora.text'], '%s', ...
			'bufsize', 4095*3);

PATH = [];
for i=2:2:length(darpa_corpora)
	if strcmp(darpa_corpora{i}, video)
		PATH = darpa_corpora{i-1};
		PATH = ['~/video-datasets/' PATH];
		break;
	end
end

if length(PATH)==0
	error(['Error: Cannot find ''' video ''' in the darpa-corpora.text']);
	return;
end

videoName = video;


if ~exist([PATH '/' videoName '/attentions'], 'dir')
	command = ['mkdir ' PATH '/' videoName '/attentions'];
	system(command);
end


frames = dir(strcat(PATH,'/',videoName,'/images/','frame-','*.ppm'));
frame_Numb = length(frames);


%%%% initialize the background by getting the average of first five frames
bk = double(imread(strcat(PATH,'/',videoName,'/images/',frames(1).name)));
[r,c,d] = size(bk);
areaFrame = size(bk,1) .* size(bk,2);
blank = zeros([r,c]);

imwrite(blank,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',1)));
imwrite(bk/255,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',1)));

% delete attentions.txt if exist
if exist([PATH '/' videoName '/attentions/attentions.txt'])
	command = ['rm ' PATH '/' videoName '/attentions/attentions.txt'];
	system(command);
end
dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), sprintf('%05d',1), '-append','delimiter','');
dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');

for ii = 2:5
    im = double(imread(strcat(PATH,'/',videoName,'/images/',frames(ii).name)));
    bk = bk + im;

    imwrite(blank,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',ii)));     %
    imwrite(im/255,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',ii)));     %

    dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), sprintf('%05d',ii), '-append','delimiter','');
    dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');
end
bk = bk./5./255;    % 0-1 double

% Gaussian filter
GF = fspecial('gaussian',8,(8+1)/6);
bk = imfilter(bk,GF,'conv','symmetric');

ii = 6;
while ii <= frame_Numb-5
%     tic;

    dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), sprintf('%05d',ii), '-append','delimiter','');
    currentOriFrame = double(imread(strcat(PATH,'/',videoName,'/images/',frames(ii).name)))/255;
    currentFrame = imfilter(currentOriFrame,GF,'conv','symmetric');

%     diffMap = (abs(currentFrame(:,:,1) - bk(:,:,1)) + abs(currentFrame(:,:,2) - bk(:,:,2)) + abs(currentFrame(:,:,3) - bk(:,:,3)))/3;
    [currentFrame_h,currentFrame_s,currentFrame_v] = rgb2hsv(currentFrame);
    [bk_h,bk_s,bk_v] = rgb2hsv(bk);

    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% modify the "s" channel %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
    maxMap_current = max(max(currentFrame(:,:,1),currentFrame(:,:,2)),currentFrame(:,:,3));
    maxMap_bk = max(max(bk(:,:,1),bk(:,:,2)),bk(:,:,3));

    c_s = zeros([r,c]);
    c_s(maxMap_current<=0.2) = 1;
    c_s = c_s .* currentFrame_s .* maxMap_current * 4;

    b_s = zeros([r,c]);
    b_s(maxMap_bk<=0.2) = 1;
    b_s = b_s .* bk_s .* maxMap_bk * 4;

    currentFrame_s(maxMap_current<=0.2) = 0;
    currentFrame_s = currentFrame_s + c_s;
    bk_s(maxMap_bk<=0.2) = 0;
    bk_s = bk_s + b_s;
    %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    diff_s = abs(currentFrame_s - bk_s);
    diff_h = abs(currentFrame_h - bk_h);
    diff_h = min(diff_h,1-diff_h);
    diffMap = max(diff_s,diff_h);

    %%%%%%%%%%%%%%%%%%% decide if need to detect %%%%%%%%%%%%%%%%%%
    targetMap = im2bw(diffMap,Th1);
    Template = strel('disk',3);
    targetMap = imopen(targetMap,Template);  % delete some small regions (considered to be the noises)

    targetAreaRatio = sum(targetMap(:)) ./ areaFrame;

    if targetAreaRatio >= TH_tar       % some objects are detected

       Template = strel('disk',20);
       targetMap = imclose(targetMap,Template);  % to combine some small regions near by each other

       [Labeled,num] = bwlabel(targetMap,8);

       % ignoring some small regions
       for jj = 1:num
           if length(find(Labeled==jj)) <= 300  % this region is small enough
              targetMap(find(Labeled==jj)) = 0;
           end
       end

       %%%%%%%%%%%%%%%%% then find each connected region again %%%%%%%%%%%%%%%%%
       [Labeled,num] = bwlabel(targetMap,8);
       ROIFrame = currentOriFrame;

       if num == 0
          dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');
       elseif num == 1
           [rows,cols] = find(Labeled==1);
           frameLine = [min(cols) min(rows) max(cols) max(rows)];
           dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), frameLine,'newline','pc','-append','delimiter',' ');
       else
           frameLine = [];
           for jj = 1:num
               [rows,cols] = find(Labeled==jj);

               frameLine = [frameLine min(cols) min(rows) max(cols) max(rows)];
               if jj == num
                  dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), frameLine,'newline', 'pc','-append','delimiter',' ');
               end
           end
       end

       for jj = 1:num
           [rows,cols] = find(Labeled==jj);
           %%%%%%%%%%%%%%%%%%%% draw the red box %%%%%%%%%%%%%%%%%%%%
           ROIFrame(min(rows):max(rows),min(cols):min(cols)+2,1) = 1;
           ROIFrame(min(rows):max(rows),max(cols)-2:max(cols),1) = 1;
           ROIFrame(min(rows):min(rows)+2,min(cols):max(cols),1) = 1;
           ROIFrame(max(rows)-2:max(rows),min(cols):max(cols),1) = 1;

           ROIFrame(min(rows):max(rows),min(cols):min(cols)+2,2) = 0;
           ROIFrame(min(rows):max(rows),max(cols)-2:max(cols),2) = 0;
           ROIFrame(min(rows):min(rows)+2,min(cols):max(cols),2) = 0;
           ROIFrame(max(rows)-2:max(rows),min(cols):max(cols),2) = 0;

           ROIFrame(min(rows):max(rows),min(cols):min(cols)+2,3) = 0;
           ROIFrame(min(rows):max(rows),max(cols)-2:max(cols),3) = 0;
           ROIFrame(min(rows):min(rows)+2,min(cols):max(cols),3) = 0;
           ROIFrame(max(rows)-2:max(rows),min(cols):max(cols),3) = 0;
       end

       imwrite(targetMap,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',ii)));
       imwrite(ROIFrame,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',ii)));

    else                               % there is no objects detected
       imwrite(blank,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',ii)));
       imwrite(currentOriFrame,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',ii)));
       dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');
    end

    ii = ii+1;


    %%%%%%%%%%%%%%%%%%% decide if need to change the backgraound %%%%%%%%%%%%%%%%
    backgroundMap = im2bw(diffMap,Th2);
    backgroundAreaRatio = sum(backgroundMap(:)) ./ areaFrame;

    if (targetAreaRatio < TH_tar) && (backgroundAreaRatio >= TH_bar)     % there is no object detected but have large area with small changes,
                                                                         % which denotes the background changes
        im1 = double(imread(strcat(PATH,'/',videoName,'/images/',frames(ii-1).name)));
        im2 = double(imread(strcat(PATH,'/',videoName,'/images/',frames(ii).name)));
        im3 = double(imread(strcat(PATH,'/',videoName,'/images/',frames(ii+1).name)));
        im4 = double(imread(strcat(PATH,'/',videoName,'/images/',frames(ii+2).name)));
        im5 = double(imread(strcat(PATH,'/',videoName,'/images/',frames(ii+3).name)));

        bk = (im1 + im2 + im3 + im4 + im5) ./ 5 ./ 255;
        bk = imfilter(bk,GF,'conv','symmetric');

        imwrite(blank,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',ii)));
        imwrite(blank,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',ii+1)));
        imwrite(blank,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',ii+2)));
        imwrite(blank,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',ii+3)));

        imwrite(im2,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',ii)));
        imwrite(im3,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',ii+1)));
        imwrite(im4,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',ii+2)));
        imwrite(im5,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',ii+3)));

        dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), sprintf('%05d',ii), '-append','delimiter','');
        dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');
        dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), sprintf('%05d',ii+1), '-append','delimiter','');
        dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');
        dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), sprintf('%05d',ii+2), '-append','delimiter','');
        dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');
        dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), sprintf('%05d',ii+3), '-append','delimiter','');
        dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');

        ii = ii + 5;
    end
%     toc;
end



%%%%%%% don't process last 5 frames %%%%%%
for ii = frame_Numb-4 : frame_Numb
    imwrite(blank,strcat(PATH,'/',videoName,'/attentions/',sprintf('fshFrame-%05d.ppm',ii)));

    currentOriFrame = imread(strcat(PATH,'/',videoName,'/images/',frames(ii).name));
    imwrite(currentOriFrame,strcat(PATH,'/',videoName,'/attentions/',sprintf('ROIBoxFrame-%05d.ppm',ii)));

    dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), sprintf('%05d',ii), '-append','delimiter','');
    dlmwrite(strcat(PATH,'/',videoName,'/attentions/','attentions.txt'), [0 0 0 0], 'newline', 'pc','-append','delimiter',' ');
end

if (0)
command = ['ffmpeg ','-i ', PATH,'/',videoName,'/attentions/fshFrame-%05d.ppm ','-vcodec ','libx264 ','-vpre ','default ','-r ','30 ','-vb ','900000 ',PATH,'/',videoName,'/attentions/',videoName,'_freeshape_combined.mov'];
system(command);
command = ['ffmpeg ','-i ', PATH,'/',videoName,'/attentions/ROIBoxFrame-%05d.ppm ','-vcodec ','libx264 ','-vpre ','default ','-r ','30 ','-vb ','900000 ',PATH,'/',videoName,'/attentions/',videoName,'_ROIBox_combined.mov'];
system(command);
end
