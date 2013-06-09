#!/bin/bash

mkdir -p ~/darpa-collaboration/pkg/sources
pushd ~/darpa-collaboration/pkg/sources
rm -f ADOL-C-2.4.0.tgz
echo "start downloading the package ..."
wget www.coin-or.org/download/source/ADOL-C/ADOL-C-2.4.0.tgz
(($? != 0)) && echo "url source expires, please manually download ADOL-C-2.4.0.tgz" && exit
popd

mkdir -p ~/darpa-collaboration/pkg/build
pushd ~/darpa-collaboration/pkg/build

mkdir -p ~/darpa-collaboration/bin/`architecture-path`
mkdir -p ~/darpa-collaboration/include/`architecture-path`
mkdir -p ~/darpa-collaboration/lib/`architecture-path`

rm -rf ADOL-C-2.4.0
tar xvf ~/darpa-collaboration/pkg/sources/ADOL-C-2.4.0.tgz
cd ADOL-C-2.4.0

## Fix some include path errors first
cp ~/sentence-training/adolc-corrected-files/{liborpar.cpp,liborser.cpp} ./ADOL-C/examples/additional_examples/openmp_exam/
cp ~/sentence-training/adolc-corrected-files/tape_handling.cpp ./ADOL-C/src/
cp ~/sentence-training/adolc-corrected-files/usrparms.h ./ADOL-C/include/adolc/

mkdir -p install
./configure --prefix=`pwd`/install --enable-docexa --enable-addexa --with-openmp-flag=-fopenmp --enable-parexa
make -j6
make install

MACHINE_TYPE=`uname -m`
LIB_NAME="lib"
if [ ${MACHINE_TYPE} == 'x86_64' ]; then
  LIB_NAME="${LIB_NAME}64"
fi

cp -r install/${LIB_NAME}/* ~/darpa-collaboration/lib/`architecture-path`/
cp -r install/include/* ~/darpa-collaboration/include/`architecture-path`/

popd

echo "adolc installed."
