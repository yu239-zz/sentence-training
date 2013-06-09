function isim = isimage(ext)

if numel(ext) & ext(1)~='.', if numel(ext) > 4, [path, name, ext] = fileparts(ext); else ext = ['.' ext]; end; end;
im_ext = [{'.png'}, {'.jpg'}, {'.jpeg'}, {'.tiff'}, {'.ppm'}, {'.bmp'}, {'.gif'}];
isim = 0;
ext = lower(ext);
for i = 1 : length(im_ext)
   if strcmp(ext, im_ext{i})
       isim = 1;
   end;
end;