#ifndef _BWLABEL_H_
#define _BWLABEL_H_
#include "cv.h"
#include "highgui.h"

extern "C"
int find( int set[], int x );

extern "C"
int bwlabel(IplImage* img, int n, int* labels);

#endif
