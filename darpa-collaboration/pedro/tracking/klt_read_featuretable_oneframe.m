function [x, y, val] = klt_read_featuretable_oneframe(filename)

str1 = '!!! Warning:  This is a KLT data file.  Do not modify below this line !!!';
preamble = 1;  % whether in preamble of file (in which comments are allowed)
counter = 0;   % current line number of file
ln_dim = 5;    % line number of file in which dimensions are specified
ln_data = 9;  % first line number of file containing data

fp = fopen(filename);
if (fp == -1)  error('Unable to open file'), end
while (~feof(fp)),
    tline = fgets(fp);
    if preamble & strncmp(tline, str1, size(str1,2)) == 1,
        preamble = 0;
    elseif ~preamble,
        if counter == ln_dim,
            a = sscanf(tline, 'nFeatures = %d');
            nframes = 1;
            nfeatures = a(1);
            x = zeros(nfeatures, nframes);
            y = zeros(nfeatures, nframes);
            val = zeros(nfeatures, nframes);
            
        elseif counter >= ln_data,
        
            % parse line
            feat_number = sscanf(tline, '%d |') + 1;
            if feat_number ~= counter - ln_data + 1, warning('Unexpected feature number'), end
            index = strfind(tline, '|');
            tline = tline( index+2 : end );
            a = sscanf(tline, '(%f,%f)=%d ');
            if size(a,1) ~= nframes*3 | size(a,2) ~= 1, warning('Unexpected number of values in row'), end
            a = reshape(a, 3, nframes);
            x(feat_number, :)   = a(1, :);
            y(feat_number, :)   = a(2, :);
            val(feat_number, :) = a(3, :);
%            disp(tline)
        end
        
        counter = counter + 1;
    end
end
if counter - ln_data ~= nfeatures, warning('Unexpected number of rows'), end
fclose(fp);