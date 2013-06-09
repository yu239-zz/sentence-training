function plotperformance_models(cls, DATASET_NAME, folders, model_names)

dataset_globals; % uses DATASET_NAME
test_path = IMAGES_TEST_PATH;
data = cell(length(folders), 1);
legendtext = cell(length(folders), 1);
for i = 1 : length(folders)
   %load(fullfile(test_path, '..', 'LSVM', sprintf('unsup_%dC%s%s', numcomp(i), parts_text, suffix), [cls '_final.mat']))
   %results_file = fullfile(test_path, 'LSVM-results', model.note, 'performance.mat');
   results_file = fullfile(test_path, 'LSVM-results', folders{i}, 'performance.mat');
   data_i = load(results_file);
   legendtext_i = ['LSVM: ' sprintf('%s (AP = %0.3f)', model_names{i}, data_i.ap)];
   legendtext{i} = legendtext_i;
   data{i} = data_i;
end;

whitebg('w');
cols = [0.1, 0.3, 1; 1, 0.1, 0.0; 0.2, 1, 0.2;  0.7, 0.1, 1; 1,0.6,0; 0,0,0];

for i = 1 : length(data)
    plot(data{i}.recall,data{i}.prec,'-','linewidth', 3, 'color', cols(i, :));  
    hold on;
end;

grid;
set(gca, 'fontsize', 14)
xlabel('recall', 'fontsize', 15)
ylabel('precision', 'fontsize', 15)
set(gca, 'XTick', [0:0.1:1]);
set(gca, 'YTick', [0:0.1:1]);
title(sprintf('class: %s',cls),'fontsize', 16);

legend(legendtext)