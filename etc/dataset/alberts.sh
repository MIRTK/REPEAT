## ALBERTs dataset information
##
## The original ALBERTs images and manual annotations are available at:
## http://brain-development.org/brain-atlases/neonatal-brain-atlas-albert/
##
## It is recommended, however, to use the modified images used
## as atlases by the MIRTK Draw-EM brain segmentation package.
## The Draw-EM ALBERTs atlas includes cGM probability maps generated
## with an automatic segmentation approach based on a probabilistic
## neonatal brain atlas. These probability maps can be used to get
## separate cortical and non-cortical labels for a better evaluation
## of the registration accuracy.
##
## Download:
## * https://www.doc.ic.ac.uk/~am411/atlases-DrawEM.html
##
## Pre-processing:
## * Run 'bin/preprocess-alberts' script.

# common top level directory of dataset images
imgdir="$HOME/Datasets/ALBERTs"

# list of image IDs
imgids=()
for i in {1..20}; do
  imgids=(${imgids[@]} $(printf "%02d" $i))
done

# list of image modalities/contrast to use for registration
chns=('t2w')

# list of image modalities/channels/contrasts used for evaluation
mods=('t2w' 'seg')

# list of ROI masks used to average voxel-wise measures
#
# - in case of a hard segmentation, an average value is computed for
#   each positive label using the binary mask of the segmentation
#   of the affinely pre-aligned target images
# - in case of an intensity image, a binary mask is created from the
#   affinely pre-aligned target image using the configured background
#   value (see get_bgvalue). If image has no background, an average is
#   computed for the entire image domain, i.e., a sum over all voxels.
rois=('msk' 'seg' 'cgm')

# ID of image used as reference for affine pre-alignment of all images
# when not set, use first image ID
refid="${imgids[0]}"

# list of image IDs used as targets
# when this list is undefined or empty, use all imgids
tgtids=("${imgids[@]}")

# list of image IDs used as source/moving images
# when this list is undefined or empty, use all imgids
srcids=("${imgids[@]}")

# parameters of preprocessing steps
# - default N4 parameters equivalent to Draw-EM pipeline v1.1
# - however, no bias correction required for Draw-EM atlases
use_N3=false
use_N4=false
arg_N4=(-c '[50x50x50,0.001]' -s 2 -b '[100,3]' -t '[0.15,0.01,200]')

# match histogram of reference image
nrmhst=true

# get file name prefix preceeding the image ID including subdirectories
get_prefix()
{
  if [ "$1" = 't2w' ]; then
    if [ "$use_N4" = true ]; then
      echo "images/t2w-n4/ALBERT_"
    elif [ "$use_N3" = true ]; then
      echo "images/t2w-n3/ALBERT_"
    else
      echo "T2/ALBERT_"
    fi
  elif [ "$1" = 'seg' ]; then
    echo "labels/ALBERT_"
  elif [ "$1" = 'cgm' ]; then
    echo "gm-posteriors-v3/ALBERT_"
  elif [ "$1" = 'msk' ]; then
    echo "masks/ALBERT_"
  fi
}

# get file name suffix following the image ID including extension
get_suffix()
{
  if [ "$1" = 'msk' ]; then
    echo "-brain.nii.gz"
  else
    echo ".nii.gz"
  fi
}

# get background value
get_bgvalue()
{
  if [ "$1" = 't1w' ] || [ "$1" = 't2w' ]; then
    echo "0"
  fi
}

# whether type of image is a binary mask
is_mask()
{
  if [ "$1" = 'msk' ]; then
    echo true
  else
    echo false
  fi
}

# whether type of image is a hard segmentation (label image)
is_seg()
{
  if [ "$1" = 'seg' ]; then
    echo true
  else
    echo false
  fi
}

# whether type of image is a probabilistic segmentation
is_prob()
{
  if [ "$1" = 'cgm' ]; then
    echo true
  else
    echo false
  fi
}
