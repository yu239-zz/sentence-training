#!/bin/bash

EXPECTED_ARGS=6
E_BADARGS=65

if [ $# -lt $EXPECTED_ARGS ]
then
  echo "Usage: `basename $0` video-name model threshold-difference nms-threshold method look-ahead"
  echo "model: person, chair, etc."
  echo "threshold-difference: change to model threshold"
  echo "nms-threshold: higher value[0,1] => less non-maximal suppression"
  echo "method: 0 - standard optical-flow 1 - KLT tracker"
  echo "look-ahead: number of frames for box prediction"
  exit $E_BADARGS
fi

matlab -nodesktop -nosplash -r "cd ~/darpa-collaboration/pedro/detector; detect_darpa_video('$1','$2',$3,$4); cd ~/darpa-collaboration/pedro/tracking; viterbi_multiple('$1','$2',$5,$6); exit;"
