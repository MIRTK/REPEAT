## NIREP dataset information
##
## Download NA0 dataset files from:
##   http://www.nirep.org/downloads

# common top level directory of dataset images
imgdir="$HOME/Datasets/NIREP"

# list of image IDs
imgids=()
for i in {1..16}; do
  imgids=(${imgids[@]} $(printf "na%02d" $i))
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

# get file name prefix preceeding the image ID including subdirectories
get_prefix()
{
  if [ $1 = 't1w' ]; then
    echo "Images/"
  elif [ $1 = 'seg' ]; then
    echo "Labels/"
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
  if [ $1 = 't1w' ]; then
    echo "0"
  fi
}
