#ifndef _VIDEO_ROI_H_
#define _VIDEO_ROI_H_

#include "cv.h"
#include "Imlib2.h"
#include "roi_box_t.h"

#ifdef DCPLUSPLUS
extern "C"
#endif
struct roi_box_t ** video_roi(Imlib_Image * images, unsigned int nimages, 
			double target_threshold,
			double background_threshold,
			double target_area_ratio_threshold,
			double background_area_ratio_threshold);


// delete video ROI linked list.
#ifdef DCPLUSPLUS
extern "C"
#endif
void deleteVideoROI(struct roi_box_t ** roi_boxes, unsigned int nimages);


#endif
