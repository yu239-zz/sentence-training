function s = procid()

d = pwd();
d = strrep(d, '\', '/');
i = strfind(d, '/');
d = d(i(end)+1:end);
s = d;
