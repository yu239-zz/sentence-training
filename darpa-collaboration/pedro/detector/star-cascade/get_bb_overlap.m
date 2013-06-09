function o = get_bb_overlap(bb1, bb2)

ty = max(bb1(1, 1), bb2(1, 1));
tx = max(bb1(1, 2), bb2(1, 2));

by = min(bb1(1, 3), bb2(1, 3));
bx = min(bb1(1, 4), bb2(1, 4));


area1 = (bb1(1, 3) - bb1(1, 1)) * (bb1(1, 4) - bb1(1, 2));
area2 = (bb2(1, 3) - bb2(1, 1)) * (bb2(1, 4) - bb2(1, 2));
w = bx - tx;
h = by - ty;
if ((w > 0) & (h > 0))
   area_int = (bx - tx) * (by - ty);
   area_union = area1 + area2 - area_int;

   o = area_int / area_union;
else
    o = 0;
end;