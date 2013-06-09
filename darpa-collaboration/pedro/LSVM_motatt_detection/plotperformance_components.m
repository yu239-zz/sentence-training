function plotperformance_components(cls, DATASET_NAME, numcomp, numparts, whichtest, suffix, outdir)

if nargin < 4, numparts = []; end;
if nargin < 5, suffix = ''; end;
dataset_globals; % uses DATASET_NAME
if strcmp(whichtest, 'test')
   test_path = IMAGES_TEST_PATH;
else
   test_path = IMAGES_TRAIN_PATH; 
end;
data = cell(length(numcomp), 1);
legendtext = cell(length(numcomp), 1);
if ~exist(outdir, 'dir'), mkdir(outdir); end;
indfound = zeros(length(numcomp), 1);

for i = 1 : length(numcomp)
   parts_text = '';
   if i <= size(numparts, 1)
       nparts = numparts(i);
       if numel(nparts), parts_text = sprintf('_%dP', nparts); end;
   end;
   %load(fullfile(test_path, '..', 'LSVM', sprintf('unsup_%dC%s%s', numcomp(i), parts_text, suffix), [cls '_final.mat']))
   %results_file = fullfile(test_path, 'LSVM-results', model.note, 'performance.mat');
   %results_file = fullfile(test_path, 'LSVM-results', sprintf('unsup_%dC%s%s', numcomp(i), parts_text, suffix), 'performance.mat');
   results_file = fullfile(test_path, 'LSVM-results', sprintf('%s_%dC%s%s', cls, numcomp(i), parts_text, suffix), 'performance.mat');
   try
      data_i = load(results_file);
       if i <= size(numparts, 1)
          legendtext_i = ['LSVM: ' sprintf('%dC,%dP (AP = %0.3f)', numcomp(i), numparts(i), data_i.ap)];
       else
          legendtext_i = ['LSVM: ' sprintf('%d comp., (AP = %0.3f)', numcomp(i), data_i.ap)];
       end;
       legendtext{i} = legendtext_i;
       data{i} = data_i;
       indfound(i) = 1;
   catch
      data_i = []; 
      legendtext{i} = ['LSVM: ' sprintf('%dC,%dP (AP = ---)', numcomp(i), numparts(i))];
   end;
end;

whitebg('w');
cols = [0.1, 0.1, 1; 1, 0.1, 0.0; 0.2, 1, 0.2;  0.7, 0.1, 1; 1,0.6,0; 0,0,0; 0.6,1,0; 0,0.6,1; 0,1,0.6; 1, 0, 0.6;
        1,0.6,0.5; 0.5,0.6,1; 0.5,1,0.6; 0.5,0.5,0.6; 0.9,1,0;1,0,1; 0,1,1];
linestyles = {'-', '-', '-', '--', '--', '--', '-.', '-.', '-.', ':', ':', ':'};
h = figure('position', [200,200,900,400]);
hold on;

for i = 1 : length(data)
    try
       plot(data{i}.recall,data{i}.prec,linestyles{1},'linewidth', 3, 'color', cols(i, :));  
       hold on;
    end;
end;

grid;
set(gca, 'fontsize', 14)
xlabel('recall', 'fontsize', 15)
ylabel('precision', 'fontsize', 15)
set(gca, 'XTick', [0:0.1:1]);
set(gca, 'YTick', [0:0.1:1]);
title(sprintf('class: %s',cls),'fontsize', 18);

legend(legendtext, 'fontsize', 14, 'location', 'NorthEastOutside')
%axis equal;

saveas(h, fullfile(outdir, [cls '_performance_' whichtest '.png']))
close(h);