#!/bin/sh

EXPECTED_ARGS=4
E_BADARGS=65

if [ $# -lt $EXPECTED_ARGS ]
then
  echo "Usage: `basename $0` in-image out-image padding model [crop-image]"
  echo "padding is a multiple of box size"
  echo "model is a mat file like person_final.mat"
  echo "crops crop-image when present"
  exit $E_BADARGS
fi

if [ -z $5 ]; then
    C=$1; else
    C=$5
fi

matlab -nodesktop -nosplash -r \
"addpath('~/darpa-collaboration/pedro/detector/'); run_and_slice('$1', '$2', $3, '$4', '$C'); exit"
