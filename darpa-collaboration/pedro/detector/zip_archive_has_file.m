function res = zip_archive_has_file(zip_filename, filename)
res = 0; % failure
if exist(zip_filename, 'file')
    cmd = sprintf('unzip -l %s | grep "%s"', zip_filename, filename);
    [status text] = system(cmd);
    if status == 0 
        res = 1; % success
    end
end
