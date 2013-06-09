function ex = fillExampleVideo(ex)

if ~isfield(ex, 'video')
    name = ex.im;
    pattern = '/([0-9]+)/frame.ppm';
    token = regexp(name, pattern, 'tokens');
    dims = size(token);
    if dims(1) == 1
       frame_no = str2num(token{1}{1});
       name = regexprep(name, pattern, '.mov');
       ex.video = name;
       ex.frame = frame_no;
    end
end
