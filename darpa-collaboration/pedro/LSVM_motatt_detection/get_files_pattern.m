function patternfiles = get_files_pattern(classfiles, pattern, directory)

patternfiles = struct(classfiles);
if ~strcmp(pattern, 'all')
    pntr = 1;
    fields = fieldnames(classfiles);
    for i = 1 : length(classfiles)
        filename = classfiles(i).name;
        [path, name, ext] = fileparts(filename);
        if numel(pattern), p = findstr(name, pattern); else p = 1; end;
        if numel(p) & isimage(ext)
            for j = 1 : size(fields, 1)
                f = getfield(classfiles(i), fields{j});
                patternfiles = setfield(patternfiles, {pntr}, fields{j}, f);
            end;
            patternfiles(pntr).imname = name;
            if nargin > 2
                patternfiles(pntr).name = fullfile(directory, filename);
            end;
            pntr = pntr + 1;
        end;
    end;

    patternfiles = patternfiles(1 : pntr - 1);
else
    for i = 1 : length(classfiles)
        filename = classfiles(i).name;
        [path, name, ext] = fileparts(filename);
        patternfiles(pntr).imname = name;
    end;
end;