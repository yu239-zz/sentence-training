
% initialize the PASCAL development kit 
%VOCdevkit = 'C:\science\data\VOC\VOCdevkit';
tmp = pwd;
cd(VOCdevkit);
%addpath([cd '/VOCcode']);
addpath(pwd);
VOCinit;
cd(tmp);
