function [xpixel, ypixel] = get_pixel_position(x, y, level, model, padx, pady)


if nargin < 5
    [padx, pady] = getpadding(model);
end;

sc = 2 ^(1/model.interval);
scale = model.sbin*sc^(level-1);  % note: should divide this by 2 of al levels are taken in detection

xpixel = (x - padx - 1) * scale + 1;
ypixel = (y - pady - 1) * scale + 1;