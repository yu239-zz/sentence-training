function video_list = training2expandscript(training_dir, bash_out_file)

if ((nargin < 1) | (numel(training_dir) == 0))
    training_dir = '/net/upplysingaoflun/aux/home/salvi/darpa-collaboration/data/C-D1b/training-data/'
end;
if ((nargin == 2) & (numel(bash_out_file) == 0))
    bash_out_file = '~/matlab/expand_training_frames.sh'
end;

models = dir(training_dir);
video_list = struct( ...
    'video_name', {}, ...
    'frames', {});

for i = 1 : length(models)
    model_name = models(i).name;
    model_dir = fullfile(training_dir, models(i).name);
    if (isfolder(model_dir))
        data_file{1} = fullfile(model_dir, [model_name '-positives.text']);
        %data_file{2} = fullfile(model_dir, [model_name '-negatives.text']);   
        for j = 1 : length(data_file)
           C = load_train_file(data_file{j});
        end;
        video_list = update_list(video_list, C);
    end;
end;
        
if ((nargin > 1) & (numel(bash_out_file)))
    fid = fopen(bash_out_file, 'w+');
    for i = 1 : length(video_list)
        video_name = video_list(i).video_name;
        fprintf(fid, './expand-video-frames ');
        fprintf(fid, '/net/perisikan/aux/fidler/tmp-video-datasets/C-D1b/videos/%s.mov ', video_name);
        frame_str = '';
        for j = 1 : length(video_list(i).frames)
            frame_str = [frame_str int2str(video_list(i).frames(j))];
            if (j < length(video_list(i).frames))
                frame_str = [frame_str ' '];
            end;
        end;
        fprintf(fid, '%s\n', frame_str);
    end;
    fclose(fid);
end;
        
        
function C = load_train_file(data_file)

fid = fopen(data_file);
C = textscan(fid, '%s %s %d %d %d %d');
fclose(fid);

function C = load_train_file_videolist(data_file)

fid = fopen(data_file);
C = textscan(fid, '%s');
fclose(fid);


function video_list = update_list(video_list, C)

for i = 1 : length(C{1})
    videoname = C{1}{i};
    frame = C{2}{i};
    fnum = str2num(frame);
    [i_video, i_frame] = find_video_in_list(video_list, videoname, fnum);
    if (i_video)
       video_list(i_video).video_name = videoname;
       video_list(i_video).frames(i_frame) = fnum;
    end;
end;


function [i_video, i_frame] = find_video_in_list(video_list, videoname, fnum)

i_video = 1;
i_frame = 1;

for i = 1 : length(video_list)
    video = video_list(i).video_name;
    if strcmp(video, videoname)
        i_video = i;
        [ind] = find(video_list(i).frames == fnum);
        if numel(ind)
            i_video = 0;
        else
            i_frame = length(video_list(i).frames) + 1;
        end;
        break;
    else
        i_video = length(video_list) + 1;
        i_frame = 1;
    end;
end;