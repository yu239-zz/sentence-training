#!/bin/bash

MBIN=`which matlab`
export MATLAB=`readlink -f $MBIN|rev|cut -d/ -f3-|rev`/
export LD_LIBRARY_PATH=${HOME}/lib/`architecture-path`/:$LD_LIBRARY_PATH:${MATLAB}/bin/glnx86/:${MATLAB}/bin/glnxa64:/usr/local/lib/:~/darpa-collaboration/lib/`architecture-path`/:/usr/local/cuda/lib64/:/usr/share/cuda-toolkit/lib64/
export PKG_CONFIG_PATH=~/darpa-collaboration/lib/`architecture-path`/pkgconfig:$PKG_CONFIG_PATH
export PYTHONPATH=${HOME}/darpa-collaboration/ideas/
export PATH=${HOME}/darpa-collaboration/bin/:$PATH:/usr/local/cuda-toolkit/bin/
