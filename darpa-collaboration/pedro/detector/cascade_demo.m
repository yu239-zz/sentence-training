function demo()

load('VOC2007/car_final');
test('000034.jpg', model);

load('INRIA/inriaperson_final');
test('000061.jpg', model);

load('VOC2007/bicycle_final');
test('000084.jpg', model);

function test(name, model)
cls = model.class;
im = imread(name);

thresh = model.thresh;
pca = 5;
csc_model = cascade_model(model, '2007', pca, thresh);

% detect objects
[dets, boxes] = cascade_detect(featpyramid(double(im), csc_model), csc_model, -0.3);
top = nms(dets, 0.5);
clf;
b = getboxes(csc_model, im, dets, boxes);
showboxes(im, b);
disp('detections');
disp('press any key to continue'); pause;
disp('continuing...');

function b = getboxes(model, image, det, all)
b = [];
if ~isempty(det)
  [det all] = clipboxes(image, det, all);
  I = nms(det, 0.5);
  det = det(I,:);
  all = all(I,:);
  b = [det(:,1:4) all];
end
