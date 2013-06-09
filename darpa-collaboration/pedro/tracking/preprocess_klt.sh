#!/bin/bash

EXPECTED_ARGS=1
E_BADARGS=65

if [ $# -lt $EXPECTED_ARGS ]
then
  echo "Usage: "
  echo "   `basename $0` darpa-video-name"
  echo "   `basename $0` directory avi-name"
  exit $E_BADARGS
fi

if [ $# -eq 2 ]
then
    ~/darpa-collaboration/klt/klt_propogate $1/$2 `~/darpa-collaboration/bin/video-length $1/$2.avi`;
fi

if [ $# -eq 1 ]
then
    dir=`dirname $1`
    video=`basename $1`

    if [[ ! $1 =~ .*"/".* ]]; then
	dir="${HOME}/video-datasets/C-D1a/SINGLE_VERB"
    fi

    find ${dir}/${video}/ -name frame.ppm -exec mogrify -format pgm {} \;
    ~/darpa-collaboration/klt/klt_propogate \
	${dir}/${video} \
	`~/darpa-collaboration/bin/video-length ${dir}/${video}.mov`;
    find ${dir}/${video}/ -name frame.pgm -exec rm {} \;
fi
