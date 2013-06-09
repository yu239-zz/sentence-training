#include "video_roi.h"
#include "cv.h"
#include "Imlib2.h"
#include "roi_box_t.h"
#include <stdlib.h>
#include <stdio.h>



void outputRoiBoxes(struct roi_box_t ** boxes, int length){
	struct roi_box_t * box;
	unsigned int i;
	for (i=0; i<length; ++i){
		printf("frame-%d\n", i+1);
		if (boxes[i] != NULL){
			box = boxes[i];
			while (box != NULL){
				printf("%f %f %f %f ", box->x1, box->y1, box->x2, box->y2);				
				box = box->next;
			}
			printf("\n");
		}
		else{
			printf("0 0 0 0\n");
		}
	}
}


int main(){
	int numOfImages = 1000;
	char imgfilename[1024];
	Imlib_Image images[1000];
	struct roi_box_t ** boxes;

	unsigned int i;

	printf("Reading in imlib_images\n\n");
	
	for (i=0; i<numOfImages; ++i){
		// you may change the following image path for your specific case.
		sprintf(imgfilename, "/home/raincol/research/darpa_tracking/yuewei/c_FoA/data/realFrame-%05d.ppm", i+1);
		images[i] = imlib_load_image(imgfilename);
	}


	// compute FoA regions
	printf("running video_roi...\n\n");

	boxes = video_roi(images, numOfImages,
			  0.15, 0.08,
			  0.005, 0.35);

	// output boxes
	printf("output roi_boxes\n\n");
	outputRoiBoxes(boxes, numOfImages);

	// delete FoA regions
	printf("deleting video ROIs\n\n");
	deleteVideoROI(boxes, numOfImages);

	return 0;
}
