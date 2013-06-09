# Makefile for darpa-collaboration/ideas
# --------------------------------------

all: error$(ERROR_OK) dsci

VERSION ?= debug
OPTFLAGS=-g3
ifeq "$(VERSION)" "release"
OPTFLAGS=-O2 -DRELEASE
endif
ifeq "$(VERSION)" "compile"
OPTFLAGS=-O0
endif

# Use this option for irobot's detection generation
#OPTIONS=-DV4L2_S2C_BACKTRACES -DUSE_IROBOT_FELZ
OPTIONS=-DV4L2_S2C_BACKTRACES

# -DQOBISCHEME_XFLUSH
# -DV4L2_S2C_JPEG_ERROR_FATAL

# Site configuration
# ------------------
INSTALLDIR  = $(QINSTALLDIR)
ARCHITECTURE_PATH = $(QARCHITECTURE_PATH)
INSTALLLIB  = $(INSTALLDIR)/lib
INSTALLLIBA = $(INSTALLLIB)/$(ARCHITECTURE_PATH)
INSTALLBIN  = $(INSTALLDIR)/bin
INSTALLBINA = $(INSTALLBIN)/$(ARCHITECTURE_PATH)

RELEASE_VERSION = 0.1
RELEASE_FILE = darpa-collaboration-$(RELEASE_VERSION)

# MATLAB_CFLAGS = $(shell mbuild -f ${MATLAB}/bin/engopts.sh -v 2> /dev/null|grep CFLAGS|cut -d= -f2-)
# MATLAB_RAW_LDFLAGS = $(shell mbuild -f ${MATLAB}/bin/engopts.sh -v 2> /dev/null|grep CLIBS|cut -d= -f2-) \
#  $(shell mbuild -f ${MATLAB}/bin/engopts.sh -v 2> /dev/null|grep LDFLAGS|cut -d= -f2-)
# MATLAB_LDFLAGS = $(MATLAB_RAW_LDFLAGS:-lstdc++=)
MATLAB_CFLAGS = -ansi -D_GNU_SOURCE -fexceptions
MATLAB_LDFLAGS = -leng -lmx -lmat

ZIP_CFLAGS = $(shell pkg-config libzip --cflags)
ZIP_LDFLAGS = $(shell pkg-config libzip --libs)

OPENCV = -I ~/darpa-collaboration/include/`architecture-path`/ \
	-L ~/darpa-collaboration/lib/`architecture-path`/ \
	-I. $(shell pkg-config --define-variable=prefix=${HOME}/darpa-collaboration/lib/`architecture-path` --cflags --libs opencv)
OPENCV_I = -I${HOME}/darpa-collaboration/include/`architecture-path`/

ADOLC_CFLAGS =
ADOLC_LDFLAGS = -ladolc

OPENMP_CFLAGS = -fopenmp
OPENMP_LDFLAGS = -fopenmp  # This can be empty if runtime lib is not needed (schedule)

LD_Q =  -L~/lib/$(QARCHITECTURE_PATH)/ \
	~/lib/$(QARCHITECTURE_PATH)/QobiScheme-AD.a  \
	~/lib/$(QARCHITECTURE_PATH)/scxl.a -lX11 -lXext -lz -lgmp

FFMPEG_LDFLAGS = -lavutil -lavformat -lavcodec -lz -lavutil -lm -lswscale

ifneq (,$(findstring -DUSE_IROBOT_FELZ,$(OPTIONS)))
IROBOTDIR = ./pkg/irobot_libcudafelz_1.2-roi
IROBOT_CFLAGS = -I $(IROBOTDIR)
IROBOT_LDFLAGS = -L $(IROBOTDIR) -lcudafelz
CUKLT_CFLAGS =
CUKLT_LDFLAGS = -lcudaklt
FLOWLIB_CFLAGS = -DCUDA_VERSION_32 -I${HOME}/darpa-collaboration/include/`architecture-path`/ -I${HOME}/darpa-collaboration/include/`architecture-path`/iu
FLOWLIB_LDFLAGS = -L ~/darpa-collaboration/lib/`architecture-path`/ -lflow -liucore -liuio
IDEALIB_CPP=idealib-cpp.cpp
CUPEDRO_CPP=cupedro.cpp
CUPEDRO_SC=cupedro-bindings.sc
endif

#DEBUG = $(OPTFLAGS) -ggdb -gdwarf-2

C_SRC = \
	hmm-control.c \
	hmm-data.c \
	hmm-def.c \
	hmm-features.c \
	hmm-rand.c \
	hmm.c \
	idealib-c.c \
	toollib-c.c \
	toollib-hack-track-c.c \
	toollib-utils.c \
	toollib-v4l2-c.c \

CPP_SRC = \
	hmm-likelihood-AD.cpp \
	$(IDEALIB_CPP) \
	$(CUPEDRO_CPP)

SC_SRC = \
	hmm-train-classify.sc \
	hmm-wbm.sc \
	idealib-hash-table.sc \
	idealib-matplotlib.sc \
	idealib-pregexp.sc \
	idealib-tracks.sc \
	idealib-stuff.sc \
	toollib-c-bindings.sc \
	toollib-camera.sc \
	toollib-hack-track-drawable.sc \
	toollib-hack-track.sc \
	toollib-image-processing.sc \
	toollib-matlab.sc \
	toollib-misc.sc \
	$(CUPEDRO_SC)

TARGET = \
	dsci \
	sentence-training \
	video-to-sentences \

# removed because they don't compile

OBJ = $(SC_SRC:.sc=.o) $(C_SRC:.c=.o) $(CPP_SRC:.cpp=.o)
SCH = $(SC_SRC:.sc=.sch)
TARGET_SRC = $(TARGET:=.sc)
TARGET_SCH = $(TARGET_SRC:.sc=.sch)
CFILES = $(SC_SRC:.sc=.c)

.SECONDEXPANSION:

${SCH}: $$(basename $$@).sc $$(SC_SRC) | in-port
	sch $(basename $@) $(SC_SRC)
	touch $(basename $@).sch

${TARGET_SCH}: $$(basename $$@).sc | in-port
	sch -main $(basename $@) $(SC_SRC)
	touch $(basename $@).sch

# -Ot -Ob -Og $(OPTFLAGS)
${TARGET}: $$(basename $$@).sc $$(basename $$@).sch $$(SCH) $(OBJ) $$@.o | environment
	scc-color -cc $(CC) $@.o $(SCCFLAGS) $(CFLAGS) $(DEBUG) $(FFMPEG_LDFLAGS) $(MATLAB_LDFLAGS) $(ZIP_CFLAGS) $(OPTFLAGS) `imlib2-config --cflags --libs` -DGL_GLEXT_PROTOTYPES $(OBJ) -o $@ $(LD_Q) $(CUDA_LDFLAGS) $(CUDA_CFLAGS) $(IROBOT_CFLAGS) $(IROBOT_LDFLAGS) $(OPENCV) $(CUKLT_CFLAGS) $(CUKLT_LDFLAGS) $(ZIP_LDFLAGS) $(FLOWLIB_CFLAGS) $(FLOWLIB_LDFLAGS) ${EXTRA_LDFLAGS} ${ADOLC_LDFLAGS} ${OPENMP_LDFLAGS}

%.o: %.sc %.sch toollib-c-macros.sch | environment
	scc-color -cc $(CC) -sch 500 -scmh 800 $(SCCFLAGS) $(DEBUG) -I. -I ~/include $< -c $(CFLAGS) $(CUDA_CFLAGS) $(ZIP_CFLAGS) $(OPTFLAGS) `imlib2-config --cflags` $(OPENCV_I) $(ADOLC_CFLAGS)

%.o: %.c | environment
	$(CC) $(DEBUG) $(CFLAGS) -DGL_GLEXT_PROTOTYPES -I ~/lib/$(ARCHITECTURE_PATH) -I. $(MATLAB_CFLAGS) $(ZIP_CFLAGS) -std=gnu99 -c $< $(OPTFLAGS) `imlib2-config --cflags` $(OPENCV_I) $(CUKLT_CFLAGS) $(FLOWLIB_CFLAGS) $(CUDA_CFLAGS) ${OPENMP_CFLAGS}

%.o: %.cpp | environment
	g++-4.4 $(DEBUG) $(CFLAGS) -DGL_GLEXT_PROTOTYPES -I. -c $< $(OPTFLAGS) `imlib2-config --cflags` $(CUDA_CFLAGS) $(IROBOT_CFLAGS) $(ZIP_CFLAGS) $(OPENCV_I) $(CUKLT_CFLAGS) $(FLOWLIB_CFLAGS) $(CUDA_CFLAGS) ${ADOLC_CFLAGS} ${OPENMP_CFLAGS}

environment:
	@if [ -z "${MATLAB}" ]; then \
		echo "Environment variables not set, prefix darpa-wrap to command"; \
		false; \
	fi;

in-port:
	@if [ ideas = `basename \`pwd\`` ]; then \
		echo "You cannot build ideas in the base directory, you must run make port and cd into the resulting directory first"; \
		false; \
	fi;

toollib-matlab.o: toollib-matlab.sc toollib-matlab.sch toollib-c-macros.sch toollib-matlab.h
	scc-color -cc $(CC) $(SCCFLAGS) $(CFLAGS) -I. -I ~/include -c toollib-matlab.sc $(MATLAB_CFLAGS) -std=gnu99
	rm toollib-matlab.c

xml-chicken: xml-chicken.sc
	csc xml-chicken.sc

tags: $(SC_SRC) $(C_SRC)
	etags -l c *.c -l scheme *.sc

# To add a new architecture, add architecture specific lines.
# The line prefix should be '#`architecture-path` ', see examples below.
port:
	-rm -rf $(ARCHITECTURE_PATH)
	mkdir $(ARCHITECTURE_PATH)
	cd $(ARCHITECTURE_PATH); csh -cf "ln -s ../*.{sc,c,cpp,h,S,sch} ."; csh -cf "ln -s ../cupedro/*.{c,cpp,h,hpp,sc,S,sch} ."
	$(MAKE) port-makefile

port-makefile:
	echo "# This file was generated automatically.  Do not edit." >\
		$(ARCHITECTURE_PATH)/makefile
	echo "# ----------------------------------------------------" >>\
		$(ARCHITECTURE_PATH)/makefile
	echo ARCHITECTURE_PATH = $(ARCHITECTURE_PATH) >>\
		$(ARCHITECTURE_PATH)/makefile
	echo ERROR_OK = _no_error >> $(ARCHITECTURE_PATH)/makefile
	grep "^#$(ARCHITECTURE_PATH)" makefile | \
		sed "s/#$(ARCHITECTURE_PATH) //" >>\
			$(ARCHITECTURE_PATH)/makefile
	echo include ../makefile >> $(ARCHITECTURE_PATH)/makefile

error:
	@echo "Do 'make port' to create an architecture specific directory."
	@exit 1

error_no_error:

# Architecture specific variables
# -------------------------------
#x86_64-Linux-2.6.34.7-56.fc13.x86_64 CC = gcc
#x86_64-Linux-2.6.34.7-56.fc13.x86_64 CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Linux-2.6.34.7-56.fc13.x86_64 SCCFLAGS = -O2
#x86_64-Linux-2.6.34.7-56.fc13.x86_64 CUDA_CFLAGS =
#x86_64-Linux-2.6.34.7-56.fc13.x86_64 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#x86_64-Gentoo-2.0.3 CC = clang -Wall
#x86_64-Gentoo-2.0.3 CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Gentoo-2.0.3 SCCFLAGS = -O2
#x86_64-Gentoo-2.0.3 CUDA_CFLAGS =
#x86_64-Gentoo-2.0.3 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#i686-Linux-2.6.26-2-686 CC = gcc
#i686-Linux-2.6.26-2-686 CFLAGS = -O2 -DLINUX $(OPTIONS)
#i686-Linux-2.6.26-2-686 SCCFLAGS = -O2
#i686-Linux-2.6.26-2-686 CUDA_CFLAGS = -I/usr/local/cuda/include/ -I/usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc
#i686-Linux-2.6.26-2-686 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#i686-Linux-2.6.32-5-686 CC = gcc
#i686-Linux-2.6.32-5-686 CFLAGS = -O2 -DLINUX $(OPTIONS)
#i686-Linux-2.6.32-5-686 SCCFLAGS = -O2
#i686-Linux-2.6.32-5-686 CUDA_CFLAGS = -I/usr/local/cuda/include/ -I/usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc
#i686-Linux-2.6.32-5-686 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#i686-Debian-5.0.3 CC = gcc
#i686-Debian-5.0.3 CFLAGS = -O2 -DLINUX $(OPTIONS)
#i686-Debian-5.0.3 SCCFLAGS = -O2
#i686-Debian-5.0.3 CUDA_CFLAGS =
#i686-Debian-5.0.3 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#x86_64-Debian-5.0.6 CC = gcc
#x86_64-Debian-5.0.6 CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Debian-5.0.6 SCCFLAGS = -O2
#x86_64-Debian-5.0.6 CUDA_CFLAGS = -I/usr/local/include/ -I/usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc
#x86_64-Debian-5.0.6 CUDA_LDFLAGS = -L/opt/cuda/lib64 -lcuda -lcudart

#x86_64-Debian-6.0.2 CC = gcc
#x86_64-Debian-6.0.2 CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Debian-6.0.2 SCCFLAGS = -O2
#x86_64-Debian-6.0.2 CUDA_CFLAGS = -I/usr/local/cuda/include/ -I/usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc
#x86_64-Debian-6.0.2 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#x86_64-Debian CC = gcc
#x86_64-Debian CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Debian SCCFLAGS = -O2
#x86_64-Debian CUDA_CFLAGS = -I/usr/local/cuda/include/ -I/usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc
#x86_64-Debian CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#x86_64-Linux-2.6.32-5-amd64 CC = gcc
#x86_64-Linux-2.6.32-5-amd64 CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Linux-2.6.32-5-amd64 SCCFLAGS = -O2
#x86_64-Linux-2.6.32-5-amd64 CUDA_CFLAGS = -I/usr/local/cuda/include/ -I/usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc
#x86_64-Linux-2.6.32-5-amd64 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#x86_64-Linux-3.2.0-3-amd64 CC = gcc-4.6
#x86_64-Linux-3.2.0-3-amd64 CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Linux-3.2.0-3-amd64 SCCFLAGS = -O2
#x86_64-Linux-3.2.0-3-amd64 EXTRA_LDFLAGS = -lblas
#x86_64-Linux-3.2.0-3-amd64 CUDA_CFLAGS = -I/usr/local/cuda/include/ -I/usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc
#x86_64-Linux-3.2.0-3-amd64 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#x86_64-Gentoo-2.1-bgc CC = clang -Wall $(OPTFLAGS) -ggdb -gdwarf-2
#x86_64-Gentoo-2.1-bgc CFLAGS = -O0 -DLINUX $(OPTIONS)
#x86_64-Gentoo-2.1-bgc SCCFLAGS = -O0
#x86_64-Gentoo-2.1-bgc CUDA_CFLAGS = -I/opt/cuda/include/ -I/opt/cuda/sdk/C/common/inc/
#x86_64-Gentoo-2.1-bgc CUDA_LDFLAGS = -L/opt/cuda/lib64 -lcuda -lcudart

#x86_64-Gentoo-2.1-thread CC = clang -Wall $(OPTFLAGS) -ggdb -gdwarf-2
#x86_64-Gentoo-2.1-thread CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Gentoo-2.1-thread SCCFLAGS = -O2
#x86_64-Gentoo-2.1-thread CUDA_CFLAGS = -I/opt/cuda/include/ -I/opt/cuda/sdk/C/common/inc/
#x86_64-Gentoo-2.1-thread CUDA_LDFLAGS = -L/opt/cuda/lib64 -lcuda -lcudart

#x86_64-Gentoo-2.2 CC = gcc -Wall $(OPTFLAGS)
#x86_64-Gentoo-2.2 CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Gentoo-2.2 SCCFLAGS = -O2
#x86_64-Gentoo-2.2 CUDA_CFLAGS = -I/opt/cuda/include/ -I/opt/cuda/sdk/C/common/inc/
#x86_64-Gentoo-2.2 CUDA_LDFLAGS = -L/opt/cuda/lib64 -lcuda -lcudart

#i686-Ubuntu-9.10 CC = gcc
#i686-Ubuntu-9.10 CFLAGS = -O2 -DLINUX $(OPTIONS)
#i686-Ubuntu-9.10 SCCFLAGS = -O2
#i686-Ubuntu-9.10 CUDA_CFLAGS = -I/usr/local/cuda/include/ -I/usr/local/NVIDIA_GPU_Computing_SDK/C/common/inc
#i686-Ubuntu-9.10 CUDA_LDFLAGS = -L/usr/local/cuda/lib64 -lcuda -lcudart

#x86_64-Arch_Linux CC = gcc
#x86_64-Arch_Linux CFLAGS = -O2 -DLINUX $(OPTIONS)
#x86_64-Arch_Linux SCCFLAGS = -O2
#x86_64-Arch_Linux EXTRA_LDFLAGS = -L/lib64/ -lstdc++ -lrt
#x86_64-Arch_Linux CUDA_CFLAGS = -I/usr/share/cuda-toolkit/include/ -I/usr/share/cuda-sdk/C/common/inc
#x86_64-Arch_Linux CUDA_LDFLAGS = -L/usr/share/cuda-toolkit/lib64 -lcuda -lcudart

# Haonan's architecture without CUDA
#i686-Ubuntu CC = gcc-4.4
#i686-Ubuntu CFLAGS = -O2 -DLINUX $(OPTIONS)
#i686-Ubuntu SCCFLAGS = -O2
#i686-Ubuntu EXTRA_LDFLAGS = -L/usr/lib/i386-linux-gnu -lstdc++

install: all
	cp $(TARGETS) $(INSTALLBINA)/.

uninstall:
	cd $(INSTALLBINA); rm -rf $(TARGETS)

.PHONY: clean real-clean environment
clean:
	-rm -f $(OBJ) $(TARGET_SRC:.sc=.c) $(TARGET_SRC:.sc=.o) \
	$(TARGET_SCH) core $(CFILES) $(SCH)

real-clean: clean
	-rm -r -f $(TARGET_SRC:.sc=)

# Automatic dependencies, don't modify them
