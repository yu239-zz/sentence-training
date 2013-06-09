function [padx, pady, sbin, interval] = get_pyrinfo_allmodels(models)
% computes max padding for all models

padx = 0;
pady = 0;
sbin = 0;
interval = 0;


for i = 1 : length(models)
    [padx_i, pady_i] = getpadding(models{i});
    padx = max(padx, padx_i);
    pady = max(pady, pady_i);
    sbin = max(sbin, models{i}.sbin);
    interval = max(interval, models{i}.interval);
end;