function filename = add_video_extension(video_filename)

if exist([video_filename '.mov'])
    filename = [video_filename '.mov'];
elseif exist([video_filename '.mp4'])
    filename = [video_filename '.mp4'];
elseif exist([video_filename '.MOV'])
    filename = [video_filename '.MOV'];
elseif exist([video_filename '.avi'])
    filename = [video_filename '.avi'];
else
    error(['could not guess video extension for ' video_filename]);
end
