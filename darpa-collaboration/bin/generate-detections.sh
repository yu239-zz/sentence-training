#!/bin/bash

(($# < 1)) && echo "Please specify the video name" && exit

VIDEO=$1

darpa-wrap $HOME/darpa-collaboration/ideas/`architecture-path`/video-to-sentences \
    -m backpack -m person -m chair -m trash-can -t 20 -verbose -beta 1 \
    -write-object-detector -write-klt -write-optical-flow \
    -cuda-object-detector -2.0 -1.2 0.55 -cuda-klt -cuda-optical-flow \
    -colour-boxes-on-the-fly -cuda-device 0 -look-ahead 2 \
    -in-place -model-path ~/darpa-collaboration/new3-models \
    -rank-box-colors -stop-before-tracker \
    -alpha 14 -new3-corpus-cfg -hmms \
    $VIDEO
