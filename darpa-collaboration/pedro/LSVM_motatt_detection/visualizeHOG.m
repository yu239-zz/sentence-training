function im = visualizeHOG(w, padx, pady, viz)

% code by Felzenswalb et al.
% visualizeHOG(w)
% Visualize HOG features/weights.

% make pictures of positive and negative weights

if (nargin < 2)
    padx = 1;
end;
if (nargin < 3)
    pady = 1;
end;

if (nargin < 4)
    viz = 1;
end;

bs = 20;
w = w(:,:,1:9);
scale = max(max(w(:)),max(-w(:)));
truncx = bs * padx;
truncy = bs * pady;

pos = HOGpicture(w, bs) * 255/scale;
pos = trunc_hog(pos, truncx, truncy);
if min(w(:)) < 0
   neg = HOGpicture(-w, bs) * 255/scale;
   neg = trunc_hog(neg, truncx, truncy);
end;

% put pictures together and draw
buff = 2;
pos = padarray(pos, [buff buff], 128, 'both');
if min(w(:)) < 0
  neg = padarray(neg, [buff buff], 128, 'both');
  im = uint8([pos; neg]);
else
  im = uint8(pos);
end
if (viz)
    clf;
    imagesc(im); 
    colormap gray;
    axis equal;
    axis off;
end;


function im = trunc_hog(im, truncx, truncy)

im = im(truncy + 1 : size(im, 1) - truncy, truncx + 1 : size(im, 2) - truncx, :);

