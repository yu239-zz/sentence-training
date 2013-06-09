function [dets,boxes,scores, bbox_overlap] = get_box_score(im, model, bboxes, overlap)

% im         image
% model      object model
% bbox       bounding box (in image coordinates) for which to compute score of model
% overlap    bbox overlap requirement
% OUTPUT: score of a hypothesis in the box

% set defaults for optional arguments
if nargin < 3
  bboxes = [];
end

if nargin < 4
  overlap = 0.85;
end

if nargin > 2 && ~isempty(bboxes)
  latent = true;
  thresh = model.thresh - 3;
else
  latent = false;
end

timeit = 1;

im = color(im);
%[im, bboxes] = cropbox(im, bboxes, padding);
%imagesc(im)
if (timeit), tic; end;
pyra = featpyramid(im, model);
pyra.im = im;
if timeit
    fprintf('Time for pyramid %0.4f\n', toc);
end;
% cache filter response
if (timeit), tic; end;
model = filterresponses(model, im, pyra, latent, bboxes, overlap);
if timeit
    fprintf('Time for filtering %0.4f\n', toc);
end;

if (timeit), tic; end;
% compute parse scores
%model = latent_locations(model, model.start, latent, pyra, bboxes, overlap);
L = model_sort(model);
for s = L
  for r = model.rules{s}
    model = apply_rule(model, r, latent, bboxes, pyra.pady, pyra.padx, pyra);
  end
  model = symbol_score(model, s);
end

% find scores above threshold
X = zeros(0, 'int32');
Y = zeros(0, 'int32');
I = zeros(0, 'int32');
L = zeros(0, 'int32');
S = [];
for level = model.interval+1:length(pyra.scales)
  score = model.symbols(model.start).score{level};
  tmpI = find(score > thresh);
  [tmpY, tmpX] = ind2sub(size(score), tmpI);
  X = [X; tmpX];
  Y = [Y; tmpY];
  I = [I; tmpI];
  L = [L; level*ones(length(tmpI), 1)];
  S = [S; score(tmpI)];
end

[ign, ord] = sort(S, 'descend');
% only return the highest scoring example in latent mode
% (the overlap requirement has already been enforced)
X = X(ord);
Y = Y(ord);
I = I(ord);
L = L(ord);
S = S(ord);
if timeit
    fprintf('Time for score computation %0.4f\n', toc);
end;

if (timeit), tic; end;
% compute detection bounding boxes and parse information
[dets, boxes, info] = getdetections(model, pyra.padx, pyra.pady, ...
 pyra.scales, X, Y, L, S);
if timeit
    fprintf('Time for extracting detection boxes %0.4f\n', toc);
    fprintf('Number of all boxes: %d\n', size(dets, 1));
end;


part_boxes = [];
if (size(dets, 1))    
    if (timeit), tic; end;
    clipdets = dets;
    % clip detection window to image boundary
    clipdets(:,1) = max(clipdets(:,1), 1);
    clipdets(:,2) = max(clipdets(:,2), 1);
    clipdets(:,3) = min(clipdets(:,3), pyra.imsize(2));
    clipdets(:,4) = min(clipdets(:,4), pyra.imsize(1));
    clipdets(:, 5) = 0;  
    dets_boxes = zeros(size(bboxes, 1), size(dets, 2));
    part_boxes = zeros(size(bboxes, 1), size(boxes, 2));
    scores = zeros(size(bboxes, 1), 1);
    bboxes_a = (bboxes(:,3)-bboxes(:,1)+1) .* (bboxes(:,4)-bboxes(:,2)+1);
    clipdets_a = (clipdets(:,3)-clipdets(:,1)+1) .* (clipdets(:,4)-clipdets(:,2)+1);
    bbox_overlap = zeros(size(bboxes, 1), 1);
    
    for i = 1 : size(bboxes, 1)
       overlaps = boxoverlap2(clipdets, bboxes(i, :), clipdets_a, bboxes_a(i, :));
       [overlap, j] = max(overlaps);    
       if (overlap == 0)
           dets_boxes(i, :) = [0, 0, 0, 0, 0, -Inf];
           scores(i, 1) = -Inf;
       else
           dets_boxes(i, :) = dets(j, :);
           part_boxes(i, :) = boxes(j, :);
           scores(i, 1) = dets(j, 6);
        end;
        bbox_overlap(i, 1) = overlap;
    end;   

        
    if timeit
       fprintf('Time for finding best boxes %0.4f\n', toc);
    end;

else
   scores = -Inf;
   bbox_overlap = 0;
end;
dets = dets_boxes;
boxes = part_boxes;


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% compute score pyramid for symbol s
function model = symbol_score(model, s)
% model  object model
% s      grammar symbol

%model = latent_locations(model, s, latent, pyra, bboxes, overlap);

% take pointwise max over scores for each rule with s as the lhs
rules = model.rules{s};
score = rules(1).score;

for r = rules(2:end)
  for i = 1:length(r.score)
    score{i} = max(score{i}, r.score{i});
  end
end
model.symbols(s).score = score;


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% compute score pyramid for rule r
function model = apply_rule(model, r, latent, bboxes, pady, padx, pyra)
% model  object model
% r      structural|deformation rule
% pady   number of rows of feature map padding
% padx   number of cols of feature map padding

if r.type == 'S'
  model = apply_structural_rule(model, r, pady, padx);
else
  model = apply_deformation_rule(model, r, latent, pyra, bboxes);
end


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% compute score pyramid for structural rule r
function model = apply_structural_rule(model, r, pady, padx)
% model  object model
% r      structural rule
% pady   number of rows of feature map padding
% padx   number of cols of feature map padding

% structural rule -> shift and sum scores from rhs symbols
% prepare score for this rule
score = model.scoretpt;
for i = 1:length(score)
  if numel(score{i})
     score{i}(:) = r.offset.w;
  end;
end

% sum scores from rhs (with appropriate shift and down sample)
for j = 1:length(r.rhs)
  ax = r.anchor{j}(1);
  ay = r.anchor{j}(2);
  ds = r.anchor{j}(3);
  % step size for down sampling
  step = 2^ds;
  % amount of (virtual) padding to halucinate
  virtpady = (step-1)*pady;
  virtpadx = (step-1)*padx;
  % starting points (simulates additional padding at finer scales)
  starty = 1+ay-virtpady;
  startx = 1+ax-virtpadx;
  % starting level
  startlevel = model.interval*ds + 1;
  % score table to shift and down sample
  s = model.symbols(r.rhs(j)).score;
  for i = startlevel:length(s)
    level = i - model.interval*ds;
    % ending points
    endy = min(size(s{level},1), starty+step*(size(score{i},1)-1));
    endx = min(size(s{level},2), startx+step*(size(score{i},2)-1));
    if ((startx <= endx) & (starty <= endy))
        % y sample points
        iy = starty:step:endy;
        oy = sum(iy < 1);
        iy = iy(iy >= 1);
        % x sample points
        ix = startx:step:endx;
        ox = sum(ix < 1);
        ix = ix(ix >= 1);
        % sample scores
        sp = s{level}(iy, ix);
        sz = size(sp);
        % sum with correct offset
        stmp = -inf(size(score{i}));
        stmp(oy+1:oy+sz(1), ox+1:ox+sz(2)) = sp;
        score{i} = score{i} + stmp;
    end;
  end
end
model.rules{r.lhs}(r.i).score = score;



%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% compute score pyramid for deformation rule r
function model = apply_deformation_rule(model, r, latent, pyra, bboxes)
% model  object model
% r      deformation rule

% deformation rule -> apply distance transform
def = r.def.w;
score = model.symbols(r.rhs(1)).score;
Ix = cell(size(score));
Iy = cell(size(score));     
padx = pyra.padx;
pady = pyra.pady;
for i = 1:length(score)
  % Note: dt has been changed so that we no longer have to pass in -score{i}
  if (numel(score{i}) > 1) 
     if ~latent
        [score{i}, Ix{i}, Iy{i}] = dt(score{i}, def(1), def(2), def(3), def(4));
     else
        [x, y] = get_box_center_locations(model, pyra, i, bboxes);
        [score{i}, Ix{i}, Iy{i}] = dtroi(score{i}, def(1), def(2), def(3), def(4), x, y); 
     end;
     score{i} = score{i} + r.offset.w;
  end;
  if numel(score{i}) == 1
      Ix{i} = 1;
      Iy{i} = 1;
  end;
end
model.rules{r.lhs}(r.i).score = score;
model.rules{r.lhs}(r.i).Ix = Ix;
model.rules{r.lhs}(r.i).Iy = Iy;


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% compute all filter responses (filter score pyramids)
function model = filterresponses(model, im, pyra, latent, bboxes, overlap)
% model    object model
% pyra     feature pyramid
% latent   true => latent positive detection mode
% bbox     ground truth bbox
% overlap  overlap threshold

% gather filters for computing match quality responses
i = 1;
filters = {};
filter_to_symbol = [];
for s = model.symbols
  if s.type == 'T'
    filters{i} = model.filters(s.filter).w;
    filter_to_symbol(i) = s.i;
    i = i + 1;
  end
end

% determine which levels to compute responses for (optimization
% for the latent=true case)
[model, levels] = validatelevels(model, pyra, latent, bboxes, overlap);

if latent
     padding = 0.1;
     [bboxes] = cropbox2(im, bboxes, padding, 0);
     bboxes = round(bboxes);
end;

for level = levels
  % compute filter response for all filters at this level
  if ~latent
     r = fconv(pyra.feat{level}, filters, 1, length(filters));
  else
     [x, y] = get_filter_locations(model, pyra, level, bboxes);
     r = fconvroi(pyra.feat{level}, filters, 1, length(filters), x, y); 
  end;
  % find max response array size for this level
  s = [-inf -inf];
  for i = 1:length(r)
    s = max([s; size(r{i})]);
  end
  % set filter response as the score for each filter terminal
  for i = 1:length(r)
    % normalize response array size so all responses at this 
    % level have the same dimension
    spady = s(1) - size(r{i},1);
    spadx = s(2) - size(r{i},2);
    r{i} = padarray(r{i}, [spady spadx], -inf, 'post');
    model.symbols(filter_to_symbol(i)).score{level} = r{i};
  end
  model.scoretpt{level} = zeros(s);
end


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% compute the overlap between bounding box and a filter at
% each filter placement in a feature map.
function o = computeoverlap(bbox, fdimy, fdimx, starty, startx, endy, endx, scale, pyra)
% bbox   bounding box image coordinates [x1 y1 x2 y2]
% fdimy  number of rows in filter
% fdimx  number of cols in filter
% dimy   number of rows in feature map
% dimx   number of cols in feature map
% scale  image scale the feature map was computed at
% padx   x padding added to feature map
% pady   y padding added to feature map

padx = pyra.padx;
pady = pyra.pady;
imsize = pyra.imsize;
imarea = imsize(1)*imsize(2);
bboxarea = (bbox(3)-bbox(1)+1)*(bbox(4)-bbox(2)+1);

% corners for each placement of the filter (in image coordinates)
x1 = ([startx:endx] - padx - 1) * scale + 1;
y1 = ([starty:endy] - pady - 1) * scale + 1;
x2 = x1 + fdimx*scale - 1;
y2 = y1 + fdimy*scale - 1;
if bboxarea / imarea < 0.7
  % clip detection window to image boundary only if
  % the bbox is less than 70% of the image area
  x1 = min(max(x1, 1), imsize(2));
  y1 = min(max(y1, 1), imsize(1));
  x2 = max(min(x2, imsize(2)), 1);
  y2 = max(min(y2, imsize(1)), 1);
end
% intersection of the filter with the bounding box
xx1 = max(x1, bbox(1));
yy1 = max(y1, bbox(2));
xx2 = min(x2, bbox(3));
yy2 = min(y2, bbox(4));

% e.g., [x1(:) y1(:)] == every upper-left corner
[x1 y1] = meshgrid(x1, y1);
[x2 y2] = meshgrid(x2, y2);
[xx1 yy1] = meshgrid(xx1, yy1);
[xx2 yy2] = meshgrid(xx2, yy2);
% compute width and height of every intersection box
w = xx2(:)-xx1(:)+1;
h = yy2(:)-yy1(:)+1;
inter = w.*h;
% a = area of (possibly clipped) detection windows
a = (x2(:)-x1(:)+1) .* (y2(:)-y1(:)+1);
% b = area of bbox
b = (bbox(3)-bbox(1)+1) * (bbox(4)-bbox(2)+1);
% intersection over union overlap
o = inter ./ (a+b-inter);
% set invalid entries to 0 overlap
o(w <= 0) = 0;
o(h <= 0) = 0;


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% ok=true if any detection window has sufficient overlap at level
% ok=false otherwise
function ok = testoverlap(level, model, pyra, bboxes, overlap)
% level    pyramid level
% model    object model
% pyra     feature pyramid
% bbox     ground truth bbox
% overlap  overlap threshold

ok = false;
scale = model.sbin/pyra.scales(level);
bboxes_s = zeros(size(bboxes, 1), 4);
for i = 1 : size(bboxes, 1)
      bbox = bboxes(i, :);
      bboxes_s(i, :) = scale_boxes(model, pyra, level, bbox, 0.6);
end;

for r = 1:length(model.rules{model.start})
  detwin = model.rules{model.start}(r).detwindow;
  for i = 1 : size(bboxes, 1)
      bbox = bboxes(i, :);
      bbox_s = bboxes_s(i, :);
      o = computeoverlap(bbox, detwin(1), detwin(2), ...
                         bbox_s(1, 2), bbox_s(1, 1), ...
                         bbox_s(1, 4), bbox_s(1, 3), ...
                         scale, pyra);
                 
      inds = find(o >= overlap);
      if ~isempty(inds)
        ok = true;
        break;
      end;
  end;
end


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% returns all levels if latent is false
% otherwise, only returns the levels that we can actual use
% for latent detections
function [model, levels] = validatelevels(model, pyra, latent, bboxes, overlap)
% model    object model
% pyra     feature pyramid
% latent   true => latent positive detection mode
% bbox     ground truth bbox
% overlap  overlap threshold

if ~latent
  levels = 1:length(pyra.feat);
else
  levels = [];
  for l = model.interval+1:length(pyra.feat)
    if ~testoverlap(l, model, pyra, bboxes, overlap)
      % no overlap at level l
      for i = 1:model.numfilters
        model.symbols(model.filters(i).symbol).score{l} = -inf;
        model.scoretpt{l} = 0;
      end
    else
      levels = [levels l l-model.interval];
    end
  end
end


  
function  [x, y] = get_filter_locations(model, pyra, level, bboxes)

     scale = model.sbin/pyra.scales(level);
     padx = pyra.padx;
     pady = pyra.pady;

% corners for each placement of the filter (in image coordinates)
     bboxes(:, [1, 3]) = round(bboxes(:, [1, 3]) / scale) + padx;
     bboxes(:, [2, 4]) = round(bboxes(:, [2, 4]) / scale) + pady;
     bboxes(:, [1, 2]) = max(ones(size(bboxes, 1), 2), bboxes(:, [1, 2]));
     bboxes(:, [3, 4]) = min(repmat([size(pyra.feat{level}, 2), size(pyra.feat{level}, 1)], [size(bboxes, 1), 1]), bboxes(:, [3, 4]));


    temp = zeros(size(pyra.feat{level}, 1), size(pyra.feat{level}, 2));   
    for i = 1 : size(bboxes, 1);
        temp(bboxes(i, 2) : bboxes(i, 4), bboxes(i, 1) : bboxes(i, 3)) = 1;
    end;
    [y, x] = find(temp);
    
    
function bboxes = scale_boxes(model, pyra, level, bboxes, padding)    

     [bboxes] = cropbox2(0, bboxes, padding, 0);
     bboxes = round(bboxes);
     scale = model.sbin/pyra.scales(level);
     padx = pyra.padx;
     pady = pyra.pady;

% corners for each placement of the filter (in image coordinates)
     bboxes(:, [1, 3]) = round(bboxes(:, [1, 3]) / scale) + padx;
     bboxes(:, [2, 4]) = round(bboxes(:, [2, 4]) / scale) + pady;
     bboxes(:, [1, 2]) = max(ones(size(bboxes, 1), 2), bboxes(:, [1, 2]));
     bboxes(:, [3, 4]) = min(repmat([size(pyra.feat{level}, 2), size(pyra.feat{level}, 1)], [size(bboxes, 1), 1]), bboxes(:, [3, 4]));

    
function [x, y] = get_box_center_locations(model, pyra, level, bboxes)

%pad = 0.45;
%startp = bboxes(:, [1, 2]) + (0.5 - pad) * (bboxes(:, [3, 4]) - bboxes(:, [1, 2]));
%endp = bboxes(:, [1, 2]) + (0.5 + pad) * (bboxes(:, [3, 4]) - bboxes(:, [1, 2]));

%bboxes = [startp, endp];
[x, y] = get_filter_locations(model, pyra, level, bboxes);
[x, n] = unique(x);
y = y(n);

        
        