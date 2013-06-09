#include "video_roi.h"
#include "Imlib2.h"
#include <math.h>
#include <cv.h>
#include <cxcore.h>
#include <stdlib.h>
#include <vector>
#include <map>
#include <utility>
#include <iostream>
#include "bwlabel.h"
#include <fstream>

using std::vector;
using std::map;

using namespace std;

extern "C"
inline float inlinemax(float a, float b) {return a>b?a:b;}

extern "C"
inline float inlinemin(float a, float b) {return a<b?a:b;}


extern "C"
float findMedian(float * array, int n);

extern "C"
int bwLabel(IplImage * I, std::vector<CvPoint> * regions);

extern "C"
void imlib2opencv(IplImage * I, Imlib_Image * images, unsigned int idx);

extern "C"
void imlib2opencv255(IplImage * I, Imlib_Image * images, unsigned int idx);

extern "C"
float findMedian255(float * array, int n);

extern "C"
int mybwlabel(IplImage * I, std::vector<std::vector<CvPoint> > & regions);

extern "C"
void cvCvtColorMatlab(IplImage * imgBGR, IplImage * imgHSV, int option);



extern "C"
struct roi_box_t ** video_roi(Imlib_Image * images, unsigned int nimages, 
				double target_threshold,
				double background_threshold,
				double target_area_ratio_threshold,
				double background_area_ratio_threshold)
{
	// variable definitions
	int span;
	int imgWidth, imgHeight, imgWidthStep, imgChannels;
	int count;
	int imgArea;

	DATA32 * imageDataPtr;
	DATA32 pixelValue;
	DATA32 pixelValueTemp;
	float * data;
	int r, g, b;
	
	imgChannels = 3;

	imlib_context_set_image(images[0]);
	imgWidth = imlib_image_get_width();
	imgHeight = imlib_image_get_height();
	imgArea = imgWidth*imgHeight;
	imgWidthStep = -1;

	
	IplImage * opencvImages[nimages];
	for (unsigned int i=0;i<nimages; ++i){
		opencvImages[i] = NULL;
	}


	// calculate background
	span = (int)floor(nimages/100);
	IplImage * backgroundImage = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
	float * bkdata = (float *)(backgroundImage->imageData);	

	float * redPixelValuesThroughFrames; 
	float * greenPixelValuesThroughFrames; 
	float * bluePixelValuesThroughFrames; 
	
	if (nimages<=200){	
		redPixelValuesThroughFrames = new float[nimages];
		greenPixelValuesThroughFrames = new float[nimages];
		bluePixelValuesThroughFrames = new float[nimages];
		for (unsigned int i=0; i<imgHeight; ++i){ // rows
			for (unsigned int j=0; j<imgWidth; ++j){ // cols
				for (unsigned int k=0; k<nimages; ++k){
					// handling red, green and blue channel
					if (opencvImages[k]==NULL){
						opencvImages[k] = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
						imlib2opencv255(opencvImages[k], images, k);
						
						if (imgWidthStep==-1){
							imgWidthStep = opencvImages[k]->widthStep/sizeof(float);
						}
					}
					data = (float*)(opencvImages[k]->imageData);
					bluePixelValuesThroughFrames[k] = data[i*imgWidthStep+j*imgChannels+0]; // b-channel
					greenPixelValuesThroughFrames[k] = data[i*imgWidthStep+j*imgChannels+1]; // g-channel
					redPixelValuesThroughFrames[k] = data[i*imgWidthStep+j*imgChannels+2]; // r-channel
				}
				bkdata[i*imgWidthStep+j*imgChannels+0] = findMedian255(bluePixelValuesThroughFrames, nimages)/255.0; // b-channel
				bkdata[i*imgWidthStep+j*imgChannels+1] = findMedian255(greenPixelValuesThroughFrames, nimages)/255.0; // g-channel
				bkdata[i*imgWidthStep+j*imgChannels+2] = findMedian255(redPixelValuesThroughFrames, nimages)/255.0; // r-channel
			}
		}

		for (unsigned int i=0; i<nimages; ++i){
			if (opencvImages[i] != NULL){
				cvReleaseImage(&opencvImages[i]);
				opencvImages[i]=NULL;
			}
		}
	}
	else{
		redPixelValuesThroughFrames = new float[100];
		greenPixelValuesThroughFrames = new float[100];
		bluePixelValuesThroughFrames = new float[100];
		for (unsigned int i=0; i<imgHeight; ++i){ // rows
			for (unsigned int j=0; j<imgWidth; ++j){ // cols
				// handling red, green and blue channel
				for (unsigned int k=0; k<100; ++k){
					if (opencvImages[k*span]==NULL){
						opencvImages[k*span] = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
						imlib2opencv255(opencvImages[k*span], images, k*span);	

						if (imgWidthStep==-1){
							imgWidthStep = opencvImages[k*span]->widthStep/sizeof(float);
						}
					}
					data = (float *)(opencvImages[k*span]->imageData);
					bluePixelValuesThroughFrames[k] = data[i*imgWidthStep+j*imgChannels+0]; // b-channel
					greenPixelValuesThroughFrames[k] = data[i*imgWidthStep+j*imgChannels+1]; // g-channel
					redPixelValuesThroughFrames[k] = data[i*imgWidthStep+j*imgChannels+2]; // r-channel
				}
				bkdata[i*imgWidthStep+j*imgChannels+0] = findMedian255(bluePixelValuesThroughFrames, 100)/255.0; // b-channel
				bkdata[i*imgWidthStep+j*imgChannels+1] = findMedian255(greenPixelValuesThroughFrames, 100)/255.0; // g-channel
				bkdata[i*imgWidthStep+j*imgChannels+2] = findMedian255(redPixelValuesThroughFrames, 100)/255.0; // r-channel

			}
		}

		for (unsigned int i=0; i<nimages; ++i){
			if (opencvImages[i] != NULL){
				cvReleaseImage(&opencvImages[i]);
				opencvImages[i]=NULL;
			}
		}
	}

	IplImage * backgroundImageSmoothed = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
	cvSmooth(backgroundImage, backgroundImageSmoothed, CV_GAUSSIAN, 9, 9, 1.5);
	IplImage * backgroundImageSmoothedHSV = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
	cvCvtColorMatlab(backgroundImageSmoothed, backgroundImageSmoothedHSV, CV_BGR2HSV);
	float * bkdataHSV = (float *)(backgroundImageSmoothedHSV->imageData);
	float * bkdataSmoothed = (float *)(backgroundImageSmoothed->imageData);

	
	// handling 0 frame to < nimages-5
	unsigned int ii=0;
	IplImage * imageSmoothed = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
	IplImage * imageSmoothedHSV = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);

	CvMat * maxMapCurrent = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * maxMapBk = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * c_s = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * b_s = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * currentFrame_s = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * currentFrame_h = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * bk_s = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * bk_h = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * diff_s = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * diff_h = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * diffMap = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * diffMap_rgb = cvCreateMat(imgHeight, imgWidth, CV_32FC1);
	CvMat * backgroundMap = cvCreateMat(imgHeight, imgWidth, CV_32FC1);

	double backgroundAreaRatio;
	double targetAreaRatio;

	float * dataHSV;
	
	float * data_pre;
	float * data_cur;
	float * data_1;
	float * data_2;
	float * data_3;

	IplImage * bwImage = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_8U, 1);
	IplImage * bwImageOpened = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_8U, 1);
	IplImage * bwImageClosed = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_8U, 1);
	unsigned char * bwImageData = (unsigned char *)(bwImage->imageData);
	unsigned char * bwImageOpenedData = (unsigned char *)(bwImageOpened->imageData);
	unsigned char * bwImageClosedData = (unsigned char *)(bwImageClosed->imageData);
	int bwImgWidthStep = bwImage->widthStep/sizeof(unsigned char);

	// remember to delete this strel at the end of this function
	IplConvKernel * strel3 = cvCreateStructuringElementEx(5, 5, 3, 3, CV_SHAPE_ELLIPSE);
	IplConvKernel * strel20 = cvCreateStructuringElementEx(39, 39, 20, 20, CV_SHAPE_ELLIPSE);
	int numOfLabels;
	vector<vector<CvPoint> > R;

	struct roi_box_t ** roi_boxes = new roi_box_t *[nimages];
	struct roi_box_t * roi_box;
	struct roi_box_t * parent_roi_box;


	while (ii<nimages-5){
		if (opencvImages[ii]==NULL){
			opencvImages[ii] = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
			imlib2opencv(opencvImages[ii], images, ii);
		}

		cvSmooth(opencvImages[ii], imageSmoothed, CV_GAUSSIAN, 9, 9, 1.5); // gaussian smoothing
		cvCvtColorMatlab(imageSmoothed, imageSmoothedHSV, CV_BGR2HSV);	// rgb2hsv
		
		data = (float *)(imageSmoothed->imageData);
		dataHSV = (float *)(imageSmoothedHSV->imageData);
		targetAreaRatio = 0;
		// modify the 'S' channel
		for (unsigned int i=0; i<imgHeight; ++i){
			for (unsigned int j=0; j<imgWidth; ++j){
				cvSetReal2D(maxMapCurrent, i, j, inlinemax(inlinemax(data[i*imgWidthStep+j*imgChannels+0], data[i*imgWidthStep+j*imgChannels+1]), 
										data[i*imgWidthStep+j*imgChannels+2]));
				cvSetReal2D(maxMapBk, i, j, inlinemax(inlinemax(bkdataSmoothed[i*imgWidthStep+j*imgChannels+0], bkdataSmoothed[i*imgWidthStep+j*imgChannels+1]),
										bkdataSmoothed[i*imgWidthStep+j*imgChannels+2]));
				cvSetReal2D(currentFrame_s, i, j, dataHSV[i*imgWidthStep+j*imgChannels+1]);
				cvSetReal2D(currentFrame_h, i, j, dataHSV[i*imgWidthStep+j*imgChannels+0]);
				cvSetReal2D(bk_s, i, j, bkdataHSV[i*imgWidthStep+j*imgChannels+1]);
				cvSetReal2D(bk_h, i, j, bkdataHSV[i*imgWidthStep+j*imgChannels+0]);

				if (cvGetReal2D(maxMapCurrent, i, j)<=0.2)
					cvSetReal2D(c_s, i, j, cvGetReal2D(currentFrame_s, i, j)*cvGetReal2D(maxMapCurrent, i, j)*4);
				else
					cvSetReal2D(c_s, i, j, 0);
	
				if (cvGetReal2D(maxMapBk, i, j)<=0.2)
					cvSetReal2D(b_s, i, j, cvGetReal2D(bk_s, i, j)*cvGetReal2D(maxMapBk, i, j)*4);
				else
					cvSetReal2D(b_s, i, j, 0);

				if (cvGetReal2D(maxMapCurrent, i, j)<=0.2)
					cvSetReal2D(currentFrame_s, i, j, 0);
				cvSetReal2D(currentFrame_s, i, j, cvGetReal2D(currentFrame_s, i, j) + cvGetReal2D(c_s, i, j));

				if (cvGetReal2D(maxMapBk, i, j)<=0.2)
					cvSetReal2D(bk_s, i, j, 0);
				cvSetReal2D(bk_s, i, j, cvGetReal2D(bk_s, i, j) + cvGetReal2D(b_s, i, j));

				cvSetReal2D(diff_s, i, j, fabs(cvGetReal2D(currentFrame_s, i, j) - cvGetReal2D(bk_s, i, j)));
				cvSetReal2D(diff_h, i, j, fabs(cvGetReal2D(currentFrame_h, i, j) - cvGetReal2D(bk_h, i, j)));

				cvSetReal2D(diff_h, i, j, min(cvGetReal2D(diff_h, i, j), 1-cvGetReal2D(diff_h, i, j)));
				cvSetReal2D(diffMap, i, j, max(cvGetReal2D(diff_s, i, j), cvGetReal2D(diff_h, i, j)));

				if (cvGetReal2D(diffMap, i, j)>target_threshold){
					bwImageData[i*bwImgWidthStep+j*1] = 1;
				}
				else{
					bwImageData[i*bwImgWidthStep+j*1] = 0;
				}
			}
		}


		// decide if need to detect
		cvMorphologyEx(bwImage, bwImageOpened, NULL, strel3, CV_MOP_OPEN);
		targetAreaRatio = 0;
		for (unsigned int i=0; i<imgHeight; ++i){
			for (unsigned int j=0; j<imgWidth; ++j){
				targetAreaRatio += bwImageOpenedData[i*bwImgWidthStep+j*1];
			}
		}
		targetAreaRatio /= (double)imgArea;
		
		// if some objects are detected
		if (targetAreaRatio>=target_area_ratio_threshold){
			cvMorphologyEx(bwImageOpened, bwImageClosed, NULL, strel20, CV_MOP_CLOSE);

			numOfLabels = mybwlabel(bwImageClosed, R);

			int minRow, maxRow, minCol, maxCol;		

			roi_boxes[ii] = NULL;
	
			// ignoring some small regions
			for (unsigned int i=0; i<numOfLabels; ++i){
				if (R[i].size()>300){

					minRow = 1e5;
					maxRow = -1e5;
					minCol = 1e5;
					maxCol = -1e5;
					for (unsigned int j=0; j<R[i].size(); ++j){
						if (minRow > R[i][j].x) minRow = R[i][j].x;
						if (maxRow < R[i][j].x) maxRow = R[i][j].x;
						if (minCol > R[i][j].y) minCol = R[i][j].y;
						if (maxCol < R[i][j].y) maxCol = R[i][j].y;
					}
					
					roi_box = new struct roi_box_t;
					roi_box->x1 = minCol;
					roi_box->y1 = minRow;
					roi_box->x2 = maxCol;
					roi_box->y2 = maxRow;
					roi_box->next = NULL;

					if (roi_boxes[ii] == NULL){
						roi_boxes[ii] = roi_box;
						parent_roi_box = roi_box;
					}
					else{
						parent_roi_box->next = roi_box;
						parent_roi_box = roi_box;
					}
				}
			}
		}
		else{
			roi_boxes[ii] = NULL;
		}

		ii++;
		
		// decide if need to change the background
		backgroundAreaRatio = 0;
		for (unsigned int i=0; i<imgHeight; ++i){
			for (unsigned int j=0; j<imgWidth; ++j){
				cvSetReal2D(diffMap_rgb, i, j, (fabs(data[i*imgWidthStep+j*imgChannels+2]-bkdataSmoothed[i*imgWidthStep+j*imgChannels+2]) + 
								fabs(data[i*imgWidthStep+j*imgChannels+1]-bkdataSmoothed[i*imgWidthStep+j*imgChannels+1]) + 
								fabs(data[i*imgWidthStep+j*imgChannels+0]-bkdataSmoothed[i*imgWidthStep+j*imgChannels+0]))/3.0);

				if (cvGetReal2D(diffMap_rgb, i, j)>background_threshold)
					cvSetReal2D(backgroundMap, i, j, 1);
				else
					cvSetReal2D(backgroundMap, i, j, 0);

				backgroundAreaRatio += cvGetReal2D(backgroundMap, i, j);
			}
		}
		backgroundAreaRatio /= (double)imgArea;

		if (targetAreaRatio < target_area_ratio_threshold && backgroundAreaRatio >= background_area_ratio_threshold){

			if (opencvImages[ii]==NULL){
				opencvImages[ii] = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
				imlib2opencv(opencvImages[ii], images, ii);
			}			
			if (opencvImages[ii+1]==NULL){
				opencvImages[ii+1] = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
				imlib2opencv(opencvImages[ii+1], images, ii+1);
			}
			if (opencvImages[ii+2]==NULL){
				opencvImages[ii+2] = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
				imlib2opencv(opencvImages[ii+2], images, ii+2);
			}			
			if (opencvImages[ii+3]==NULL){
				opencvImages[ii+3] = cvCreateImage(cvSize(imgWidth, imgHeight), IPL_DEPTH_32F, imgChannels);
				imlib2opencv(opencvImages[ii+3], images, ii+3);
			}

			data_pre = (float *)(opencvImages[ii-1]->imageData);
			data_cur = (float *)(opencvImages[ii]->imageData);
			data_1 = (float *)(opencvImages[ii+1]->imageData);
			data_2 = (float *)(opencvImages[ii+2]->imageData);
			data_3 = (float *)(opencvImages[ii+3]->imageData);

			for (unsigned int i=0; i<imgHeight; ++i){
				for (unsigned int j=0; j<imgWidth; ++j){
					for (unsigned int k=0; k<3; ++k){
						bkdata[i*imgWidthStep+j*imgChannels+k] = (data_pre[i*imgWidthStep+j*imgChannels+k] + 
											  data_cur[i*imgWidthStep+j*imgChannels+k] +
											  data_1[i*imgWidthStep+j*imgChannels+k] +
											  data_2[i*imgWidthStep+j*imgChannels+k] +
											  data_3[i*imgWidthStep+j*imgChannels+k])/5.0;
					}
				}
			}
		
			cvSmooth(backgroundImage, backgroundImageSmoothed, CV_GAUSSIAN, 9, 9, 1.5);
			cvCvtColorMatlab(backgroundImageSmoothed, backgroundImageSmoothedHSV, CV_BGR2HSV);	

			cvReleaseImage(&opencvImages[ii-1]);
			opencvImages[ii-1] = NULL;
			cvReleaseImage(&opencvImages[ii]);
			opencvImages[ii] = NULL;
			cvReleaseImage(&opencvImages[ii+1]);
			opencvImages[ii+1] = NULL;
			cvReleaseImage(&opencvImages[ii+2]);
			opencvImages[ii+2] = NULL;
			cvReleaseImage(&opencvImages[ii+3]);
			opencvImages[ii+3] = NULL;

			roi_boxes[ii] = NULL;
			roi_boxes[ii+1] = NULL;
			roi_boxes[ii+2] = NULL;
			roi_boxes[ii+3] = NULL;

			ii += 4;
		}
		else{
			cvReleaseImage(&opencvImages[ii-1]);
			opencvImages[ii-1]=NULL;
		}
	}

	// handling the last five frames, copied from the last 6-th box
	struct roi_box_t * node;
	for (ii=nimages-5; ii<nimages; ++ii){
		node = roi_boxes[nimages-6];
		if (node == NULL)
			roi_boxes[ii] = NULL;
		else{
			roi_boxes[ii] = NULL;
			while (node != NULL){
				roi_box	= new struct roi_box_t;				
				roi_box->x1 = node->x1;
				roi_box->y1 = node->y1;
				roi_box->x2 = node->x2;
				roi_box->y2 = node->y2;
				roi_box->next = NULL;
				
				if (roi_boxes[ii] == NULL){
					roi_boxes[ii] = roi_box;
					parent_roi_box = roi_box;
				}
				else{
					parent_roi_box->next = roi_box;
					parent_roi_box = roi_box;
				}

				node = node->next;
			}	
		}
	}


	//===============================================================================================
	// clean up
	//===============================================================================================
	// free the IplImage * images
	for (unsigned int i=0; i<nimages; ++i){
		if (opencvImages[i] != NULL)
			cvReleaseImage(&opencvImages[i]);
	}

	// free the images
	if (backgroundImage != NULL) cvReleaseImage(&backgroundImage);
	if (backgroundImageSmoothed != NULL) cvReleaseImage(&backgroundImageSmoothed);
	if (backgroundImageSmoothedHSV != NULL) cvReleaseImage(&backgroundImageSmoothedHSV);
	if (imageSmoothed != NULL) cvReleaseImage(&imageSmoothed);
	if (imageSmoothedHSV != NULL) cvReleaseImage(&imageSmoothedHSV);
	if (bwImage != NULL) cvReleaseImage(&bwImage);
	if (bwImageOpened != NULL) cvReleaseImage(&bwImageOpened);
	if (bwImageClosed != NULL) cvReleaseImage(&bwImageClosed);


	// free pixelValuesThroughFrames: red, green, blue.
	delete [] redPixelValuesThroughFrames;
	delete [] bluePixelValuesThroughFrames;
	delete [] greenPixelValuesThroughFrames;

	// free cvMat
	if (maxMapCurrent != NULL) cvReleaseMat(&maxMapCurrent);
	if (maxMapBk != NULL) cvReleaseMat(&maxMapBk);
	if (c_s != NULL) cvReleaseMat(&c_s);
	if (b_s != NULL) cvReleaseMat(&b_s);
	if (currentFrame_s != NULL) cvReleaseMat(&currentFrame_s);
	if (currentFrame_h != NULL) cvReleaseMat(&currentFrame_h);
	if (bk_s != NULL) cvReleaseMat(&bk_s);
	if (bk_h != NULL) cvReleaseMat(&bk_h);
	if (diff_s != NULL) cvReleaseMat(&diff_s);
	if (diff_h != NULL) cvReleaseMat(&diff_h);
	if (diffMap != NULL) cvReleaseMat(&diffMap);
	if (diffMap_rgb != NULL) cvReleaseMat(&diffMap_rgb);
	if (backgroundMap != NULL) cvReleaseMat(&backgroundMap);

	// free strel3 and strel20
	cvReleaseStructuringElement(&strel3);
	cvReleaseStructuringElement(&strel20);

	return roi_boxes;
}

extern "C"
void cvCvtColorMatlab(IplImage * imgBGR, IplImage * imgHSV, int option){
	cvCvtColor(imgBGR, imgHSV, option);

	float * dataHSV = (float *)imgHSV->imageData;
	int imgHeight = imgHSV->height;
	int imgWidth = imgHSV->width;
	int imgWidthStep = imgHSV->widthStep/sizeof(float);
	int imgChannels = imgHSV->nChannels;

	for (unsigned int i=0; i<imgHeight; ++i){
		for (unsigned int j=0; j<imgWidth; ++j){
			dataHSV[i*imgWidthStep+j*imgChannels+0] /= 360.0;
		}
	}
}


extern "C"
int mybwlabel(IplImage * I, std::vector<std::vector<CvPoint> > & regions){

	int imgWidth = I->width;
	int imgHeight = I->height;
	int imgStep = I->widthStep;
	int channels = I->nChannels;

	int numOfRegions = 0;
	int * labels = new int[imgWidth*imgHeight];
	numOfRegions = bwlabel(I, 8, labels);

	regions.clear();
	regions.resize(numOfRegions);

	CvPoint pt;

	for (unsigned int i=0; i<imgHeight; ++i){
		for (unsigned int j=0; j<imgWidth; ++j){
			pt.x = i;
			pt.y = j;
			if (labels[i*imgWidth+j]!=0)
				regions[labels[i*imgWidth+j]-1].push_back(pt);
		}	
	}
	
	delete [] labels;

	return numOfRegions;
}

extern "C"
float findMedian(float * array, int n){
	float temp;
	int i,j;
	for (i=n-1; i>=0; i--){
		for (j=0; j<=i; j++){
			if (array[j]>=array[j+1]){
				temp = array[j];
				array[j] = array[j+1];
				array[j+1] = temp;
			}
		}
	}
	if (n%2==0)
		return (array[n/2]+array[n/2+1])/2.0;
	else
		return array[n/2+1];
}


extern "C"
float findMedian255(float * array, int n){
	float bins[256];
	float medianValue = 0;
	float sumCum = 0;
	float medianValue1 = 0;
	float medianValue2 = 0;
	for (unsigned int i=0; i<256; ++i){
		bins[i]=0;	
	}
	for (unsigned int i=0; i<n; ++i){
		bins[(int)array[i]] += 1;	
	}

	if (n%2==1){
		sumCum = 0;
		for (unsigned int i=0; i<256; ++i){
			sumCum += bins[i];
			if ((n/2+1)<=sumCum){
				medianValue = (float)i;
				break;
			}
		}
	}
	else{
		sumCum = 0;
		for (unsigned int i=0; i<256; ++i){
			sumCum += bins[i];
			if (n/2<=sumCum){
				medianValue1 = (float)i;
				break;
			}
		}	
		sumCum = 0;
		for (unsigned int i=0; i<256; ++i){
			sumCum += bins[i];
			if ((n/2+1)<=sumCum){
				medianValue2 = (float)i;
				break;
			}
		}
		medianValue = (medianValue1+medianValue2)/2.0;
	}

	return medianValue;
}

extern "C"
void deleteVideoROI(struct roi_box_t ** roi_boxes, unsigned int nimages){
	
	struct roi_box_t * roi_box_ptr;
	struct roi_box_t * roi_box_ptr_temp;

	for (unsigned int i=0; i<nimages; ++i){
		roi_box_ptr = roi_boxes[i];
		while (roi_box_ptr != NULL){
			roi_box_ptr_temp = roi_box_ptr;
			roi_box_ptr = roi_box_ptr->next;
			delete roi_box_ptr_temp;
		}
	}

	// release roi_box_t * roi_boxes[nimages];
	delete [] roi_boxes;
}


extern "C"
void imlib2opencv(IplImage * I, Imlib_Image * images, unsigned int idx){
	imlib_context_set_image(images[idx]);
	DATA32 * imageDataPtr = imlib_image_get_data_for_reading_only();

	DATA32 pixelValue;
	DATA32 pixelValueTemp;

	float * data = (float *)(I->imageData);
	int imgWidth = imlib_image_get_width();
	int imgHeight = imlib_image_get_height();
	int imgWidthStep = I->widthStep/sizeof(float);
	int imgChannels = I->nChannels;
	
	int b, g, r;

	for (unsigned int j=0; j<imgHeight; ++j){ // rows
		for (unsigned int k=0; k<imgWidth; ++k){ // cols

			pixelValue = imageDataPtr[j*imgWidth+k];
			// obtain r g b values in range [0, 255],
			// r second 8 bits, g third 8 bits, b last 8 bits
			// from most signficant to less signficant
			// alpha channel is the first 8 bits
			
			pixelValueTemp = pixelValue>>16;
			r = pixelValueTemp & ((DATA32)255);

			pixelValueTemp = pixelValue>>8;
			g = pixelValueTemp & ((DATA32)255);

			pixelValueTemp = pixelValue;
			b = pixelValueTemp & ((DATA32)255);


			// in opencv, the color is stored in BGR way.
			data[j*imgWidthStep+k*imgChannels+0] = (float)b/255.0;
			data[j*imgWidthStep+k*imgChannels+1] = (float)g/255.0;
			data[j*imgWidthStep+k*imgChannels+2] = (float)r/255.0;				

		}
	}
}


extern "C"
void imlib2opencv255(IplImage * I, Imlib_Image * images, unsigned int idx){
	         imlib_context_set_image(images[idx]);
	         DATA32 * imageDataPtr = imlib_image_get_data_for_reading_only();
	 
	         DATA32 pixelValue;
	         DATA32 pixelValueTemp;
	 
	         float * data = (float *)(I->imageData);
	         int imgWidth = imlib_image_get_width();
	         int imgHeight = imlib_image_get_height();
	         int imgWidthStep = I->widthStep/sizeof(float);
	         int imgChannels = I->nChannels;
	 
	         int b, g, r;
	
	         for (unsigned int j=0; j<imgHeight; ++j){ // rows
	                 for (unsigned int k=0; k<imgWidth; ++k){ // cols
	 
	                         pixelValue = imageDataPtr[j*imgWidth+k];
	                         // obtain r g b values in range [0, 255],
	                         // r second 8 bits, g third 8 bits, b last 8 bits
	                         // from most signficant to less signficant
	                         // alpha channel is the first 8 bits
	 
	                         pixelValueTemp = pixelValue>>16;
	                         r = pixelValueTemp & ((DATA32)255);
	 
	                         pixelValueTemp = pixelValue>>8;
	                         g = pixelValueTemp & ((DATA32)255);
	 
	                         pixelValueTemp = pixelValue;
	                         b = pixelValueTemp & ((DATA32)255);
	 
	 
	                         // in opencv, the color is stored in BGR way.
	                         data[j*imgWidthStep+k*imgChannels+0] = (float)b;
	                         data[j*imgWidthStep+k*imgChannels+1] = (float)g;
	                         data[j*imgWidthStep+k*imgChannels+2] = (float)r;
	 
	                 }
	         }

}
