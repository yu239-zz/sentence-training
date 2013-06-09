LSVM_Motatt_Detection: deformable_model lsvm learn
	mcc -m LSVM_motatt_single_video_detection_script.m

deformable_model:
	mex -O resize.cc
	mex -O dt.cc
	mex -O dtroi.cc
	mex -O features.cc
	mex -O getdetections.cc
	mex -O fconvblas.cc -lmwblas -o fconv
	mex -O fconvroiMT.cc -o fconvroi

learn: learn.cc
	g++ -O3 -D_FILE_OFFSET_BITS=64 -o learn learn.cc

lsvm:
	mex -O -DMATLAB_MEX -I./ mexsvmlearn.c global.c svm_learn.c svm_common.c svm_hideo.c mexcommon.c
	mex -O -DMATLAB_MEX -I./ mexsvmclassify.c global.c svm_learn.c svm_common.c svm_hideo.c mexcommon.c
	mex -O -DMATLAB_MEX -I./ mexsinglekernel.c global.c svm_learn.c svm_common.c svm_hideo.c mexcommon.c
	mex -O -DMATLAB_MEX -I./ mexkernel.c global.c svm_learn.c svm_common.c svm_hideo.c mexcommon.c

clean:
	rm -rf LSVM_motatt_single_video_detection_script
	rm -rf LSVM_motatt_single_video_detection_script_main.c 
	rm -rf LSVM_motatt_single_video_detection_script_mcc_component_data.c 
	rm -rf LSVM_motatt_single_video_detection_script.prj 
	rm -rf mccExcludedFiles.log 
	rm -rf run_LSVM_motatt_single_video_detection_script.sh 
	rm -rf readme.txt
	rm -rf learn
	rm -rf *.mexa64 *.mexglx *.mexsol *.mexw64 *.mexw32
