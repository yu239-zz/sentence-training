# Sentence Training

This file contains the release of the code for Grounded Language
Learning from Video Described with Sentences, Haonan Yu and Jeffrey
Mark Siskind, ACL 2013. For other resources, please see the 
[project page](http://haonanyu.com/research/acl2013/).

Most of the infrastructure was developed by the Purdue-University of
South Carolina-University of Toronto team under the DARPA Mind's Eye
program. The core algorithm of *sentence training* was developed by
the CCCP group at Purdue University.

Components of the infrastructure were written by:
```
   Andrei Barbu
   Alexander Bridge
   Daniel Barrett
   Ryan Buffington
   Zachary Burchill
   Yu Cao
   Tommy Chang
   Dan Coroian
   Sven Dickinson
   Sanja Fidler
   Alex Levinshtein
   Yuewei Lin
   Sam Mussman
   Siddharth Narayanaswamy
   Dhaval Salvi
   Lara Schmidt
   Jiangnan Shangguan
   Jeffrey Mark Siskind
   Aaron Michaux
   Jarrell Waggoner
   Song Wang
   Jinliang Wei
   Yifan Yin
   Haonan Yu
   Zhiqi Zhang
```
and others.

# License

All code written by the Purdue-lead team, including the code in
ideas/, is copyright Purdue University 2010, 2011, 2012, and 2013.
All rights reserved.

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.

This archive contains a number of off-the-shelf packages.  These are covered
by their respective licenses.

# Instructions

Getting this package set up is not for the faint of heart, it has many
dependencies. It also depends on closed-source code which must be
obtained from iRobot. See below. The installation has been tested on
both 32bits- and 64bits-linux machines with matlab2010a and
gcc-4.4. There may be some changes to be made by the user besides
those listed below.

Make sure you have matlab installed. The matlab binary must be in
$PATH and it must be a symlink of the form
<toplevel-matlab-directory>/bin/matlab. The include and lib
directories should be in the include and library path, or you could
specify them in 

```bash
~/sentence-training/darpa-collaboration/ideas/makefile
```

The contents of this archive must be unpacked into your home directory
and will create an ~/sentence-training/ directory.

We provide two versions of the sentence-training code. The full
version requires CUDA installed and the closed-source code from irobot
and it can generate object detections and compute optical flows from
raw video. The presets for the full-version code:

Uncomment
```bash
#OPTIONS=-DV4L2_S2C_BACKTRACES -DUSE_IROBOT_FELZ
```
in ~/sentence-training/darpa-collaboration/ideas/makefile
```bash
cd ~/sentence-training/darpa-collaboration/ideas
cp ./with-cuda-irobot/* ./
cd -
```

CUDA must be installed at /usr/local/cuda such that
/usr/local/cuda/include/ contains the header files.

The CUDA SDK must be installed at /usr/local/NVIDIA_GPU_Computing_SDK/
such that /usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc contains
the header files.

iRobot's implementation of the star detector cannot be shipped 
with this archive and must be acquired directly from iRobot. This 
tar file must be placed in the sentence-training directory if you 
want to generate detections from raw video:

```bash
irobot_libcudafelz_1.2-roi-9999.tar.gz
```

In case that the above requirements can't be met, we also offer 
precomputed optical flows and detections within the dataset. The 
short-version code can read these precomputed stuff for training. 

For either version, before running the installation, you have to
specify your system architecture with respect to these three
makefiles:

```bash
~/sentence-training/install-i686 (w.r.t ~/sentence-training/scheme2c/makefile)
~/sentence-training/QobiScheme-makefile
~/sentence-training/darpa-collaboration/ideas/makefile
```

Please search keywords 'Haonan's architecture' in these files to see
how to specify them. Note that the options in each makefile should
depend on your own system.

To install this package first append the contents of dot-bashrc
to your .bashrc file
```
  cat dot-bashrc >> ~/.bashrc
```
and then execute
``` 
  ./run
```

'run' will prompt for root permissions for only one operation before
beginning the setup:
```
  sudo ./packages.sh
```
This will run a number of
```
  apt-get install -y
```
commands to fetch packages which this code depends on.

This code is mostly-self-contained. On my system *i686-Ubuntu*, it will install:
-  an ~/.ffmpeg directory with an ffmpeg preset file required to
    produce consistent output when rendering video
-  ~/bin/i686-Ubuntu, ~/lib/i686-Ubuntu, and ~/include/i686-Ubuntu which
    contain the installed Scheme->C and QobiScheme infrastructure
-  ~/darpa-collaboration which contains our codebase

~/darpa-collaboration/ideas contains the code for the sentence-training
pipeline.

To build the pipeline execute
```
  darpa-wrap make port
  cd `architecture-path`
  darpa-wrap make -j6 sentence-training
```
in ~/darpa-collaboration/ideas. If you don't have CUDA or irobot's package, 
there may be several warnings during compiling. You can just ignore them.

Two examples of the pipeline are executed at the end of the run script:

```bash
~/darpa-collaboration/bin/sentence-training.sh ~/sentence-training/sample-dataset
~/darpa-collaboration/bin/sentence-likelihood.sh ~/sentence-training/sample-dataset \
    "The person approached the trash-can" MVI_0820.mov \
    ~/sentence-training/sample-dataset/new3-hand-models.sc /tmp/result.sc
```

The first one is to train word models from a small sample dataset. The second
one is to compute the likelihood for a video-sentence pair given hand-written 
models.

If you want to generate detections from raw video in the beginning, you have 
to do additionally
```bash
darpa-wrap make -j6 video-to-sentences
```
besides making sentence-training. Then execute the script 
```bash
~/darpa-collaboration/bin/generated-detections.sh <your-video-path>
```


# A simple walkthrough of the core

The code is composed of Scheme, C/C++ code and some other languages. The 
high-level control and prepocessing are handled by Scheme code. The low-level 
training algorithm is written in C/C++ code.

The highest-level control of the algorithm is in file sentence-training.sc, with
the entry of the program: 
``` scheme
(define-command
  (main
```

Some preprocessings are executed after this, including setting different 
kinds of parameters, reading/generating object detections and optical flows, 
initializing HMMs, etc. Then the code will call the following functions in sequence:
``` scheme
                                (sentence-training-iterative-multiple) 
;; Randomly initialize the HMM parameters for a certain amount of times, then 
;; pick the trained models 
;; with the best result. This is done to avoid bad local minima.
                                                 |
                                                 |
                                  (sentence-training-iterative-one)
;; This is the main loop of iteratively updating the HMM parameters.
                                                 |
                                                 |
                                                / \
                                              /     \
                                            /         \
                                          /             \
                  (sentence-training-multiple)       (gt-estimation-multiple)
;; Both these two functions estimate the parameters from multiple training samples inside one iteration. 
;; They will use ML-based and DT-based estimation algorithms respectively. ML-based one is the algorithm 
;; proposed in the ACL 2013 paper. DT-based one is going to be presented in another paper.
                               |                                  |
                               |                                  |
                     (sentence-training-one)              (gt-estimation-one)
;; Estimate the parameters from one training sample. The estimation results will accumulated back to the 
;; upper level functions.
                               |                                  |
                               |                                  |
                          (update-x!)                (sentence-derivatives-one-video)
;; The scheme bindings for C/C++ functions.
```

Bindings between Scheme and C/C++ code can be found in hmm-wbm.sc, idealib-tracks.sc, and idealib-stuff.sc.

Inside C/C++ code, the estimation algorithms reside mainly in hmm-control.c (ML) and hmm-likelihood-AD.cpp 
(DT). Other files contain low-level helper functions.


Finally, the trained models will be returned by (sentence-training-iterative-multiple), together 
with the objective function value. 

# Acknowledgements

This work was supported, in part, by NSF grant CCF-0438806, by the
Naval Research Laboratory under Contract Number N00173-10-1-G023, by
the Army Research Laboratory accomplished under Cooperative Agreement
Number W911NF-10-2-0060, and by computational resources provided by
Information Technology at Purdue through its Rosen Center for Advanced
Computing.  Any views, opinions, findings, conclusions, or
recommendations contained or expressed in this document or material
are those of the author(s) and do not necessarily reflect or represent
the views or official policies, either expressed or implied, of NSF,
the Naval Research Laboratory, the Office of Naval Research, the Army
Research Laboratory, or the U.S. Government.  The U.S. Government is
authorized to reproduce and distribute reprints for Government
purposes, notwithstanding any copyright notation herein.
