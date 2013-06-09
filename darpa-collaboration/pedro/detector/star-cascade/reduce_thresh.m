function csc_model = reduce_thresh(csc_model, thresh_diff)

for i = 1 : length(csc_model.cascade.t)
   csc_model.cascade.t{i} = csc_model.cascade.t{i} + thresh_diff; 
end;