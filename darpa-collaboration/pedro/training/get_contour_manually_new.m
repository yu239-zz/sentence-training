function get_contour_manually_new(dirname,corpus,model,listoffset,outputFileName,numsamp)

addpath('~/darpa-collaboration/ffmpeg/')
openVid=0;
filenames=textread([dirname '/' model '.text'],'%s');
first_display = false;
handle = 0;

numsamp = min(numsamp, length(filenames) / 2 - 1);

disp('Gathering Positive Samples, you may skip a frame by hitting enter');
figure;
for k=listoffset:2:length(filenames)

    disp(['video list offset: ' num2str(k) ': ' filenames{k,1}]);

    lookahead=0;
    tmpres=mod(k,2);
    if tmpres==0
	framenumber=filenames{k,1};
	foldername=filenames{k-1,1};
        lookahead=min(k+1, length(filenames) - 1);

    else
	framenumber=filenames{k+1,1};
	foldername=filenames{k,1};
        lookahead=min(k+2, length(filenames) - 1);
    end

    video_filename = add_video_extension([getenv('HOME') '/video-datasets/' corpus '/' foldername]);

    if k==1 || k==listoffset
       ffmpegOpenVideo(video_filename);
    end

    if k~=length(filenames)
    toClose=strcmp(foldername,filenames{lookahead,1});
    end

    if k~=1 && openVid==1
	ffmpegOpenVideo(video_filename);
    openVid=0;
    end


    %% GUI to get box co ordinates

    i=1;
    not(ffmpegIsFinished())
    i~=str2num(framenumber)

    while(not(ffmpegIsFinished()) && i~=str2num(framenumber))

      i=ffmpegNextFrame();
      continue;
    end

    posfile= [dirname '/' model '-' outputFileName '.text'];
    isthere=exist(posfile);

    if(isthere==2)
	syscmd=['cat ' posfile ' |wc -l'];
	[status,linecount]=system(syscmd);
		if(str2num(linecount)>=numsamp)
		    disp(['you have already collected numsamp (', num2str(numsamp), ') samples']);
		    close;
		    ffmpegCloseVideo();
                    createNegative(dirname, corpus, model, 100);
		    return;
                end
	end
	disp(['framenumber: ' framenumber]);
	im=ffmpegGetFrame();

	[h,w,t] = size(im);

		if ~first_display
		handle=imshow(im);
			set(handle,'EraseMode','none');
			first_display=true;
		else
			set(handle,'CData',im);
			drawnow;
		end

		p = [];
	p = ginput(2);

	if(size(p,1) < 2)
	    if k~=length(filenames) && toClose==0
	    openVid=1;
	    ffmpegCloseVideo();
            createNegative(dirname, corpus, model, 100);
	     end
	    continue;
	end

	x1=int32(min(p(1,2),p(2,2)));
	x2=int32(max(p(1,2),p(2,2)));
	y1=int32(min(p(1,1),p(2,1)));
	y2=int32(max(p(1,1),p(2,1)));

		if (x1 < 0 || x2 < 0 || y1 < 0 || y2 < 0 || x1 > h || x2 > h || y1 > w || y2 > w)
	     if k~=length(filenames) && toClose==0
		 openVid=1;
	    ffmpegCloseVideo();
            createNegative(dirname, corpus, model, 100);
	     end
			continue;
	end

	if (abs(x1-x2) < 20 || abs(y1-y2) < 20)
	    if k~=length(filenames) && toClose==0
		openVid=1;
	    ffmpegCloseVideo();
            createNegative(dirname, corpus, model, 100);
	    end

	    continue;
	end

	%% writing out videoname, framenumber , groundtruth co-ordinates


	output=[foldername ' ' framenumber  ' '  num2str(x1) ' ' num2str(y1) ' ' num2str(x2) ' ' num2str(y2)];

	dlmwrite([dirname '/' model '-' outputFileName '.text'],output,'delimiter','','-append');

	% If you interrupt the process early, be sure to call this before restarting it (or just restart matlab)

    if k~=length(filenames) && toClose==0
	openVid=1;
    ffmpegCloseVideo();
    createNegative(dirname, corpus, model, 100);
    end    
end
ffmpegCloseVideo();
createNegative(dirname, corpus, model, 100);

close;
