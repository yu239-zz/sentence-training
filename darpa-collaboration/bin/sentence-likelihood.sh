#!/bin/bash

show_help(){
    cat <<EOF
Usage:

./sentence-likelihood.sh dataset-path plain-sentence video-name trained-models output-file
e.g.,
./sentence-likelihood.sh ~/sentence-training/sample-dataset "The person approached the trash-can" MVI_0820.mov ~/sentence-training/sample-dataset/new3-hand-models.sc /tmp/result.sc
EOF
}

(($# < 5)) && show_help && exit

VIDEO_PATH=$1 && shift && SENTENCE=$1 && shift && VIDEO=$1 && shift && MODEL=$1 && shift && WRITE_PATH=$1

darpa-wrap $HOME/darpa-collaboration/ideas/`architecture-path`/sentence-training \
    -m backpack -m person -m chair -m trash-can \
    -new3-corpus-cfg -verbose \
    -prediction-lookahead 2 -upper-triangular \
    -model-path $HOME/darpa-collaboration/new3-models -top-n 3 \
    -video-directory $VIDEO_PATH \
    -compute-likelihood "`echo $SENTENCE | tr + \ `" "$VIDEO"  "$WRITE_PATH" \
    -file-model "$MODEL"