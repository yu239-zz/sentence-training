function createNegative(dirname,corpus,model,nrperframe)

    if nargin < 4
        nrperframe = 2;
    end

    addpath('~/darpa-collaboration/ffmpeg/')
    openVid=0;
    filenames=textread([dirname '/' model '-positives.text'],'%s','delimiter','\n');

    numofsamples=length(filenames);
    temp=textread([dirname '/' model '-positives.text'],'%s');

    disp(numofsamples);

    for i=1:numofsamples

        disp(['framenumber:' num2str(i)]);

        videoname=temp{(6*(i-1)+1),1};
        frame=temp{(6*(i-1)+2),1};

        y1=str2num(temp{(6*(i-1)+3),1});
        x1=str2num(temp{(6*(i-1)+4),1});
        y2=str2num(temp{(6*(i-1)+5),1});
        x2=str2num(temp{(6*(i-1)+6),1});

        if i==1
            ffmpegOpenVideo(add_video_extension([getenv('HOME') '/video-datasets/' corpus '/' videoname]));
        end

        if i~=numofsamples
            toClose=strcmp(videoname,temp{(6*(i-1)+1),1});
        end

        if i~=1 && openVid==1
            ffmpegOpenVideo(add_video_extension([getenv('HOME') '/video-datasets/' corpus '/' videoname]));
            openVid=0;
        end

        m=1;

        while(not(ffmpegIsFinished()) && m~=str2num(frame))

            m=ffmpegNextFrame();
            continue;
        end

        groundtruth=ffmpegGetFrame();

        %groundtruth=imread([getenv('HOME') '/video-datasets/C-D1/recognition/' videoname '/' frame '/frame.ppm']);

        %% creating co-ordinates for negative sample by selecting negative box
        %% which does not overlap with groundtruth
        for z=1:nrperframe

            a=size(groundtruth,2);
            b=size(groundtruth,1);
            c=1;

            r = uint16(c + (a-c).*rand(1,1));
            b1=r(1,1);
            s= uint16(c + (b-c).*rand(1,1));
            b2=s(1,1);
            t = uint16(c + (a-c).*rand(1,1));
            b3=t(1,1);
            u= uint16(c + (b-c).*rand(1,1));
            b4=u(1,1);
            outbox=zeros(b,a);
            outboxcounter=0;

            while outboxcounter==0

                r = uint16(c + (a-c).*rand(1,1));
                b1=r(1,1);
                s= uint16(c + (b-c).*rand(1,1));
                b2=s(1,1);
                t = uint16(c + (a-c).*rand(1,1));
                b3=t(1,1);
                u= uint16(c + (b-c).*rand(1,1));
                b4=u(1,1);

                while ((b3<b1)||(b4<b2))  || ((abs(b3-b1)<200) || (abs(b4-b2)<200))
                    r = uint16(c + (a-c).*rand(1,1));
                    b1=r(1,1);
                    s= uint16(c + (b-c).*rand(1,1));
                    b2=s(1,1);
                    t = uint16(c + (a-c).*rand(1,1));
                    b3=t(1,1);
                    u= uint16(c + (b-c).*rand(1,1));
                    b4=u(1,1);

                end

                ground1=zeros(b,a);

                ground1(y1:y2,x1:x2)=1;
                ground2=zeros(b,a);
                ground2(b2:b4,b1:b3)=1;

                %for m=1:b
                %   for n=1:a
                %      outbox(m,n)=ground1(m,n) && ground2(m,n);
                % end

                %end

                box1=zeros(4,1);
                box1(1,1)=y1;
                box1(2,1)=x1;
                box1(3,1)=y2;
                box1(4,1)=x2;
                box1=box1';

                box2=zeros(4,1);
                box2(1,1)=b2;
                box2(2,1)=b1;
                box2(3,1)=b4;
                box2(4,1)=b3;
                box2=box2';

                scorebox=get_bb_overlap(box1, box2);

                %if outbox==0
                if scorebox < 0.5
                    outboxcounter=1;
                end

            end

            %%%%%%%%%%%%%%%%%%%

            %%writing out videoname,framenumber,negative box co-ordinates
            %    negative=groundtruth(b2:b4,b1:b3);

            negy1=b2;
            negx1=b1;
            negy2=b4;
            negx2=b3;

            output=[videoname ' ' num2str(frame)  ' ' num2str(z) ' ' num2str(negy1) ' ' num2str(negx1) ' ' num2str(negy2) ' ' num2str(negx2)];

            dlmwrite([dirname  '/' model '-negatives.text'],output,'delimiter','','-append');

        end

        if i~=numofsamples && toClose==0
            openVid=1;
            ffmpegCloseVideo();
        end

        if i==numofsamples
            ffmpegCloseVideo();
        end

    end

end
