OPENCV_INCLUDE_PATH = -I/usr/local/include/opencv -I/usr/local/include/opencv2/core -I/usr/local/include/opencv2/calib3d -I/usr/local/include/opencv2/flann -I/usr/local/include/opencv2/legacy -I/usr/local/include/opencv2/objdetect -I/usr/local/include/opencv2/ts -I/usr/local/include/opencv2/contrib -I/usr/local/include/opencv2/features2d -I/usr/local/include/opencv2/gpu -I/usr/local/include/opencv2/imgproc -I/usr/local/include/opencv2/ml -I/usr/local/include/opencv2/video

OPENCV_LIBRARY_PATH = -L/usr/local/lib

OPENCV_LIBRARIES = -lopencv_core -lopencv_ml -lopencv_imgproc -lopencv_highgui -lopencv_calib3d -lopencv_features2d -lopencv_flann -lopencv_gpu -lopencv_legacy -lopencv_objdetect -lopencv_ts -lopencv_video

IMLIB2_INCLUDE_PATH = -I/home/raincol/installed/lib/imlib2-1.4.5/include

IMLIB2_LIBRARY_PATH = -L/home/raincol/installed/lib/imlib2-1.4.5/lib -L/home/raincol/installed/lib/imlib2-1.4.5/lib/imlib2/filters -L/home/raincol/installed/lib/imlib2-1.4.5/lib/imlib2/loaders

IMLIB2_LIBRARIES = -lImlib2


test:test.o video_roi.o bwlabel.o
	gcc -lstdc++ -O2 -o test test.o video_roi.o bwlabel.o $(OPENCV_INCLUDE_PATH) $(OPENCV_LIBRARY_PATH) $(OPENCV_LIBRARIES) $(IMLIB2_LIBRARY_PATH) $(IMLIB2_LIBRARIES) $(IMLIB2_INCLUDE_PATH) $(IMLIB2_INCLUDE_PATH) $(IMLIB2_LIBRARIES) -lm

test.o:test.c
	gcc -O2 -c test.c $(OPENCV_INCLUDE_PATH) $(OPENCV_LIBRARY_PATH) $(OPENCV_LIBRARIES) $(IMLIB2_LIBRARY_PATH) $(IMLIB2_LIBRARIES) $(IMLIB2_INCLUDE_PATH) $(IMLIB2_INCLUDE_PATH) $(IMLIB2_LIBRARIES)

video_roi.o:video_roi.h video_roi.cpp roi_box_t.h bwlabel.h
	g++ -DDCPLUSPLUS -O2 -c video_roi.cpp $(OPENCV_INCLUDE_PATH) $(OPENCV_LIBRARY_PATH) $(OPENCV_LIBRARIES) $(IMLIB2_LIBRARY_PATH) $(IMLIB2_LIBRARIES) $(IMLIB2_INCLUDE_PATH) $(IMLIB2_INCLUDE_PATH) $(IMLIB2_LIBRARIES) -lm

bwlabel.o:bwlabel.cpp bwlabel.h
	g++ -O2 -c bwlabel.cpp $(OPENCV_INCLUDE_PATH) $(OPENCV_LIBRARY_PATH) $(OPENCV_LIBRARIES) $(IMLIB2_LIBRARY_PATH) $(IMLIB2_LIBRARIES) $(IMLIB2_INCLUDE_PATH) $(IMLIB2_INCLUDE_PATH) $(IMLIB2_LIBRARIES)


clean:
	rm -rf test *.o
