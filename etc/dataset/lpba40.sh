## LONI LPBA40 dataset information
##
## Download:
## * http://www.loni.usc.edu/atlases/Atlas_Detail.php?atlas_id=12
##   - "LPBA40 Subjects Delineation Space: MRI and label files in delineation space"
##
## Pre-processing:
## * Extract images in delineation space and set 'imgdir' below.
## * Run 'bin/preprocess-lpba40' script.

# common top level directory of dataset images
imgdir="$HOME/Datasets/LPBA40/delineation_space"

# list of image IDs
imgids=()
for i in {1..40}; do
  imgids=(${imgids[@]} $(printf "S%02d" $i))
done

# kind of images used for registration
chns=('t1w')

# kind of images used for evaluation
mods=('t1w' 'seg')

# list of ROI masks used to average voxel-wise measures
#
# - in case of a hard segmentation, an average value is computed for
#   each positive label using the binary mask of the segmentation
#   of the affinely pre-aligned target images
# - in case of an intensity image, a binary mask is created from the
#   affinely pre-aligned target image using the configured background
#   value (see get_bgvalue). If image has no background, an average is
#   computed for the entire image domain, i.e., a sum over all voxels.
rois=('t1w' 'seg')

# ID of image used as reference for affine pre-alignment of all images
# when not set, use first image ID
refid="${imgids[0]}"

# list of image IDs used as targets/fixed images
# when this list is undefined or empty, all imgids are used
tgtids=("${imgids[@]:0:4}")

# list of image IDs used as source/moving images
# when this list is undefined or empty, all imgids are used
srcids=("${imgids[@]}")

# parameters of preprocessing steps
use_N4=false  # default N3 was better for LPBA40 dataset...
arg_N4=(-c '[50x50x50,0.001]' -s 2 -b '[100,3]' -t '[0.15,0.01,200]')

# get file name prefix preceeding the image ID including subdirectories
get_prefix()
{
  if [ "$1" = 't1w' ]; then
    if [ "$use_N4" = true ]; then
      echo "images/t1w-n4/"
    else
      echo "images/t1w-n3/"
    fi
  elif [ "$1" = 'seg' ]; then
    echo "labels/"
  fi
}

# get file name suffix following the image ID including extension
get_suffix()
{
  echo ".nii.gz"
}

# get background value
get_bgvalue()
{
  if [ "$1" = 't1w' ]; then
    echo "0"
  fi
}
