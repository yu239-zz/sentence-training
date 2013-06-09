function model = bboxpred_dummy(model)

nrules = length(model.rules{model.start});
bboxpred = cell(nrules, 1);
empty=zeros(1,model.sbin*2+3);
for c = 1:nrules
	bboxpred{c}.x1 = empty;
	bboxpred{c}.y1 = empty;
    bboxpred{c}.x2 = empty;
    bboxpred{c}.y2 = empty;
end
model.bboxpred = bboxpred;
