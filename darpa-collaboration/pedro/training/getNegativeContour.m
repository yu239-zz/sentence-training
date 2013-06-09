function [ output_args ] = getNegativeContour(dirname,model)
%UNTITLED3 Summary of this function goes here
%   Detailed explanation goes here

disp('Gathering Negative Samples');

filenames=textread([getenv('HOME') '/' dirname '/' model '-positives.text'],'%s','delimiter','\n');



numofsamples=length(filenames);
temp=textread([getenv('HOME') '/' dirname '/' model '-positives.text'],'%s');
figure;

for i=1:numofsamples
    
    foldername=temp{(6*(i-1)+1),1};
    frame=temp{(6*(i-1)+2),1};
    
    
    
    im=imread([getenv('HOME') '/video-datasets/C-D1/recognition/' foldername '/' frame '/frame.ppm']);
    
    imshow(im);
    
	[h,w,t] = size(im);

	repeat_selection = true;

	while(repeat_selection)
		repeat_selection = false;

	    p = ginput(2);
    
	    if(size(p,1) < 2)
			repeat_selection = true;
	    end

	    negx1=int32(min(p(1,2),p(2,2)));
	    negx2=int32(max(p(1,2),p(2,2)));
    	negy1=int32(min(p(1,1),p(2,1)));
	    negy2=int32(max(p(1,1),p(2,1)));
    
		if (negx1 < 0 || negx2 < 0 || negy1 < 0 || negy2 < 0 || negx1 > h || negx2 > h || negy1 > w || negy2 > w)
			repeat_selection = true;
			disp('Invalid coordinates');
		end
        
		if (abs(negx1-negx2) < 100 || abs(negy1-negy2) < 100)
			repeat_selection = true;
			disp('Too small');
		end
	end
    
    %% writing out videoname, framenumber , groundtruth co-ordinates
    %         frame=sprintf('%08d',i);
    
    output=[foldername ' ' num2str(frame)  ' '  num2str(negx1) ' ' num2str(negy1) ' ' num2str(negx2) ' ' num2str(negy2)];
    
    dlmwrite([getenv('HOME') '/' dirname '/' model '-negatives.text'],output,'delimiter','','-append');    
    
end

close;

end

