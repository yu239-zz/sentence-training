function [pos] = createDataMatrixCascade(video_root, model_dir, model)

disp('Creating Data Matrix');

fid = fopen([model_dir '/' model '-positives.text']);
C = textscan(fid, '%s %s %d %d %d %d');
fclose(fid);

pos(length(C{1}))=struct;
fprintf('Num. of all positive examples: %d\n', length(C{1}));

for i=1:length(C{1})
    pos(i).im=[video_root '/' C{1}{i} '/' C{2}{i} '/frame.ppm' ];
    y1 = C{3}(i, :); x1 = C{4}(i, :); y2 = C{5}(i, :); x2 = C{6}(i, :);
    pos(i).y1=double(y1);
    pos(i).x1=double(x1);
    pos(i).y2=double(y2);
    pos(i).x2=double(x2);
    pos(i).flip=false;
    pos(i).video = add_video_extension([video_root '/' C{1}{i}]);
    pos(i).frame = str2num(C{2}{i});
    pos(i).image = [];
end
end
