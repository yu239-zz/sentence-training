#!/bin/bash

(( $# < 1 )) && echo "Please specify the video path" && exit 1

VIDEO_PATH=$1

darpa-wrap $HOME/darpa-collaboration/ideas/`architecture-path`/sentence-training \
    -m backpack -m person -m chair -m trash-can -negatives-replace-verbs \
    -new3-corpus-cfg -randomise-model -ml 1 \
    -verbose -no-duplicate-models \
    -prediction-lookahead 2 -pos n -pos v -pos p -pos pm -pos adv \
    -maximum-iteration 100 \
    -model-path $HOME/darpa-collaboration/new3-models -top-n 2 \
    -restart-times 1 -noise-delta 0.3 -tolerance 1e-3 -d-delta -0.7 \
    -d-step 10 -maximum-d 15 -initialized-d 2 -upper-triangular \
    -video-directory $VIDEO_PATH \
    -training-samples $VIDEO_PATH/training/one-sample-train.sc
