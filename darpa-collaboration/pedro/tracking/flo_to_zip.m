function [status] = flo_to_zip(zip_filename, dest_filename, data)
    % Never let an overwrite happen (we may need to create a del_from_zip
    % function to compensate for this behaviour)
    if zip_archive_has_file(zip_filename, dest_filename)
       fprintf(2, 'Refusing to overwrite file %s in zip archive %s\n', dest_filename, zip_filename); 
       status = 255;
       return
    end

    % Create temporary working directory
    [status tmpdir] = system('mktemp -d /tmp/matlab_dlmwrite-to-zip.XXXXXXXX');
    tmpdir = strtrim(tmpdir); % system keeps annoying return character
    
    abs_dest_filename = sprintf('%s/%s', tmpdir, dest_filename);
    
    % Make sure destination directory exists for temporary file
    status = system(['mkdir -p "$(dirname "' abs_dest_filename '")"'] );
    if status ~= 0
        cleanup_tmpdir(tmpdir);
        return
    end
    
    % Write data to tmpdir
    writeFlowFile(data, abs_dest_filename);
    
    % We need the root of dest_filename, since that is what
    % needs to be moved into the zip file
    root = dest_filename;
    while ~fileparts(root)
       root = fileparts(root); 
    end
    
    % Move data file into zip file
    status = system(['cd ' tmpdir ' &&  zip ' zip_filename ' "' root '" 1>/dev/null']);
    if status ~= 0 
        cleanup_tmpdir(tmpdir);
        return    
    end
    
    % Cleanup
    cleanup_tmpdir(tmpdir);
end


function cleanup_tmpdir(tmpdir)
    system(['rm -rf ' tmpdir]);
end
