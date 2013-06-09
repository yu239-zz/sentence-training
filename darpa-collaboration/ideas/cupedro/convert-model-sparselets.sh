#!/bin/bash

EXEC="$(basename "$0")"
DETECTORDIR="$HOME/darpa-collaboration/pedro/detector"
IROBOTDIR="$HOME/darpa-collaboration/pkg/irobot_libcudafelz_1.3_x86_64_toolkit3.2"
SPAMSARC="$HOME/darpa-collaboration/pkg/sources/SPAMS_v2.1_mkl64.tar.gz"
SPAMSHTTP="http://www.di.ens.fr/willow/SPAMS/"
TMPD="$(mktemp -d "/tmp/$EXEC-XXXXXXXXXX")"
# Uncomment below to debug
# TMPD=$HOME/tmp
TMPF="$TMPD/convert.m"

# Default parameters
DICTSIZE=400
SPARSITY=28
RESULTSD="$(pwd)"

# --- be a good citizen
trap clean_up EXIT
clean_up()
{
    # Uncomment below do debug
    # echo "TODO fix me"
    rm -rf "$TMPD"
    stty sane
}

# --- the absolute pathname of a file
abs_path()
{
    FILENAME="$1"
    echo "$(cd "$(dirname "$FILENAME")"; pwd)/$(basename "$FILENAME")"
}

# --- remove a file extension
extensionless()
{
    FILENAME="$1"
    EXT="$(echo "$FILENAME" | awk -F . '{if (NF>1) { print $NF }}')"
    EXTENSIONLESS="$FILENAME"
    (( ${#EXT} > 0 )) && EXTENSIONLESS="${FILENAME:0:$(expr ${#FILENAME} -  ${#EXT} - 1)}"
    echo "$EXTENSIONLESS"
}

# --- help message
show_help()
{
    cat <<EOF

   Usage: $EXEC [--outputdir DIRECTORY] [--dict-size N] [--sparsity L] [model.mat]+

      Converts vanilla felzenswalb models into irobot's cuda-sparslets format.
      The model file /must/ be accompanied by a pca0 statistics file which is
      generated as a by-product of converting vanilla models to star-cascade models.
      Running this script /without/ the pca0 file will result in a sensible
      error message that should help you jump through this hoop if you need to.

      Make sure you read the README file in the irobot directory. There is
      important information regarding using the sparslets models within the 
      c++ code.

   Options:

      --outputdir DIRECTORY  Where to store the output files. Default is \$(pwd).
      --dict-size N          Dictionary size for sparslets. Default is $DICTSIZE.
      --sparsity L           Sparsity parameter. Default is $SPARSITY.

      May crash if for unreasonable values of N and L.

   Example:

      # Creates 4 files in \$(pwd), with dict-size $DICTSIZE and sparcity $SPARSITY
      $EXEC bicycle.mat closet.mat person.mat rake.mat

   Depends:

      irobot distribution unpacked at:
         $IROBOTDIR

      A working copy of Felzenszwalb at:
         $DETECTORDIR

      The SPAMS_v2.1_mkl64 tarball located at:
         $SPAMSARC

      The SPAMS tarball can be downloaded from $SPAMSHTTP

EOF
}

# ---------------------------------------------------------------------------- Parse arguments
(( $# == 0 )) && echo "Expected at least 1 argument, showing help" 1>&2 && show_help && exit 1
(( $# > 0 )) && [ "$1" = "-h" ] || [ "$1" = "--help" ] && show_help && exit 0

# ------------------------------------------------------------------------- Check dependencies
! [ -d "$DETECTORDIR" ] && echo "Failed to find felzenszwalb directory: $DETECTORDIR, aborting." 1>&2 && exit 1
! [ -d "$IROBOTDIR" ] && echo "Failed to find irobot directory: $IROBOTDIR aborting." 1>&2 && exit 1
! [ -e "$SPAMSARC" ] && echo "Failed to find SPAMS installation file: $SPAMSARC, it can be downloaded from $SPAMSHTTP. Aborting." 1>&2 && exit 1


# ------------------------------------------------------------------------ Build matlab script
cat > $TMPF <<EOF
% -- Run from the detector directory
addpath('$TMPD/detector/SPAMS/release/mkl64/'); 
addpath('$TMPD/detector/SPAMS/test_release'); 
addpath('$TMPD/detector/star-cascade'); 
addpath('$TMPD/detector/sparselets');
addpath('$TMPD/detector');
addpath('$TMPD');

filenames = [];

EOF

NMODELS=0
while (( $# > 0 )) ; do
    FILE="$1"
    shift
    if [ "$FILE" = "--outputdir" ] ; then
	(( $# == 0 )) && echo "Expected a direcotry after --outputdir, aborting." 1>&2 && exit 1
	RESULTSD="$(abs_path "$1")"
	! [ -d "$RESULTSD" ] && echo "Directory does not exist: '$RESULTSD', aborting." 1>&2 && exit 1
	shift
    elif [ "$FILE" = "--dict-size" ] ; then
	(( $# == 0 )) && echo "Expected an integer after --dict-size, aborting." 1>&2 && exit 1
	[ "$1" -ne "$1" 2>/dev/null ] && echo "Expected an integer after --dict-size, aborting." 1>&2 && exit 1
	DICTSIZE="$1"
	shift
    elif [ "$FILE" = "--sparsity" ] ; then
	(( $# == 0 )) && echo "Expected an integer after --sparsity aborting." 1>&2 && exit 1
	[ "$1" -ne "$1" 2>/dev/null ] && echo "Expected an integer after --sparsity aborting." 1>&2 && exit 1
	SPARSITY="$1"
	shift
    else
	! [ -e "$FILE" ] && echo "File not found: $FILE, aborting" 1>&2 && exit
	ABSFILE="$(abs_path "$FILE")"
	echo "filenames{end+1} = '$ABSFILE'" >> $TMPF
	NMODELS=$(expr $NMODELS + 1)
    fi
done

(( $NMODELS == 0 )) && echo "You must specify at least 1 model, aborting" 1>&2 && exit 1

cat >> $TMPF <<EOF

try

   result_dir = '$RESULTSD';

   classes = [];
   ncls = length(filenames);
   for i = 1:ncls
      filename = filenames{i};
      m = load(filename);
      clazz = m.model.class;
      classes{end+1} = clazz;
      % Does the PCA file exist?
      pca_filename = regexprep(filename, ['/' clazz '.mat'], ['/' clazz '_cascade_data_pca0.inf']);
      if ~exist(pca_filename, 'file')
         fprintf(2, 'Failed to find pca0 statistics file %s.\n', pca_filename);
         fprintf(2, 'Cowardly refusing to continue.\n');
         fprintf(2, 'This file is generated as a by-product of creating cascade-models.\n');
         fprintf(2, 'Try running the script convert_to_cascade_model.m.\n\n');
         error('File not found');
      end
   end

   make_sparselet_models($DICTSIZE, $SPARSITY, classes, filenames, result_dir) 

catch

   fprintf(2, 'An exception was raised\n');

end

% -- Exit matlab
exit


EOF

# --------------------------------------------------- ---------- Build irobots export facility
echo "Copying files to temporary directory $TMPD"
cp -r $DETECTORDIR $TMPD/
cd $TMPD/detector
cp -r $IROBOTDIR/matlab/voc-release4/* .
cat $SPAMSARC | gunzip -dc | tar xf -

echo "Build matlab felz with irobot's model.cc file"
cd $TMPD/detector/star-cascade
! make && echo "Failed to build irobot's model exporting facility, aborting..." && exit 1

# ------------------------------------------------------------------------  Execute
echo "Executing matlab script:"
echo
cat "$TMPF" | sed 's/^/   /'
echo

cd "$TMPD"

export LD_LIBRARY_PATH=$TMPD/detector/SPAMS/libs_ext/mkl64/
export DYLD_LIBRARY_PATH=$TMPD/detector/SPAMS/libs_ext/mkl64/
export KMP_DUPLICATE_LIB_OK=true
matlab -nodesktop -nosplash -r "addpath('$TMPD'); convert" >/dev/null
echo
echo "// ~ - Done - ~ //"
echo

# Matlab does something to the terminal, and this resets it
stty sane


