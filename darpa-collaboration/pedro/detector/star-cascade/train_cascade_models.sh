#!/bin/bash

EXPECTED_ARGS=5
E_BADARGS=65

if [ $# -lt $EXPECTED_ARGS ]
then
  echo "Usage: `basename $0` experiment models suffix thresh-difference max-num-boxes"
  echo "experiment: 1 or 2, check code"
  echo "models: 1 or [1:5]'"
  echo "suffix: suffix for output directories"
  echo "thresh-difference: change to model threshold"
  echo "max-num-boxes: number of additional training boxes to take"
  exit $E_BADARGS
fi

matlab -nodesktop -nosplash -r "cd ~/matlab; addpath(genpath(pwd)); exp=$1; ind_models=$2; suffix=$3; thresh_diff=$4; max_per_frame=$5; generate_cascade_trainingdata_script; exit;"
matlab -nodesktop -nosplash -r "cd ~/matlab; addpath(genpath(pwd)); exp=$1; ind_models=$2; suffix=$3; thresh_diff=$4; cascade_train_script; exit;"
