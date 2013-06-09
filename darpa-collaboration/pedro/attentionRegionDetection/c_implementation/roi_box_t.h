#ifndef _ROI_BOX_T_
#define _ROI_BOX_T_

struct roi_box_t{
	double x1, y1, x2, y2;	
	struct roi_box_t * next;
};

#endif
