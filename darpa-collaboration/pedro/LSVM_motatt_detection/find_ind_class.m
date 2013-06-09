function j = find_ind_class(classinfo, cls)

for i = 1 : length(classinfo)
    if strcmp(classinfo(i).class, cls)
        j = i;
    end;
end;