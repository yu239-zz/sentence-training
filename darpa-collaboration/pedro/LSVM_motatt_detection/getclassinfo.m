function [classinfo, gtcols] = getclassinfo()

classes = {'void', 'building', 'grass', 'tree', 'cow', 'horse', 'sheep', 'sky', 'mountain', 'aeroplane', 'water',...
           'face', 'car', 'bicycle', 'flower', 'sign', 'bird', 'book', 'chair', 'road', 'cat', 'dog', 'body', 'boat'};

gtcols = [0,   0,  0;  % 'void' 1
          128, 0, 0;   % 'building' 2
          0, 128, 0;   % 'grass' 3
          128, 128, 0; % 'tree' 4
          0,  0, 128;  % 'cow' 5
          128, 0, 128; % 'horse' 6
          0,128,128;   % 'sheep' 7
          128,128,128; % 'sky' 8
          64,0,0;      % 'mountain' 9
          192,0,0;     % 'aeroplane' 10
          64, 128, 0;  % 'water' 11
          192, 128, 0; % 'face' 12
          64, 0, 128; % 'car' 13
          192, 0, 128; % 'bicycle' 14
          64, 128, 128; % 'flower' 15
          192, 128, 128; % 'sign' 16
          0, 64, 0;     % 'bird' 17
          128, 64, 0;   % 'book' 18
          0, 192, 0;    % 'chair' 19
          128, 64, 128; % 'road' 20
          0, 192, 128;  % 'cat' 21
          128, 192, 128; % 'dog' 22
          64, 64, 0;    % 'body' 23
          192, 64, 0;    % 'boat' 24
          ]; 
      
classinfo = struct('class', classes);
for i = 1 : length(classes)
    classinfo(i).color = gtcols(i, :);
end;