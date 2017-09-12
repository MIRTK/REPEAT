## OASIS MAL35 dataset information
##
## From MICCAI 2012 Grand Challenge and Workshop on Multi-Atlas Labeling.
## Manual segmentations of 35 adult brain images of the OASIS database
## were provided for this challenge by Neuromorphometrics Inc.
##
## Download:
## * http://masiweb.vuse.vanderbilt.edu/workshop2012/
## * http://www.oasis-brains.org/
##
## Images used for MIRTK evaluation pre-processed by Christian Ledig:
## * N4 bias field correction.
## * Brain masks computed with PINCRAM (http://soundray.org/pincram/)

# common top level directory of dataset images
imgdir="$HOME/Datasets/MAL35"

# list of all image IDs (incl. 2nd scans)
# this variable is unused; you may set imgids=(${allids[@]}) below
allids=(
  1000 1001 1002 1003 1004 1005 1006 1007 1008 1009 1010
  1011 1012 1013 1014 1015 1017 1018 1019 1023 1024 1025
  1036 1038 1039 1101 1104 1107 1110 1113 1116 1119 1122
  1125 1128
)

# list of image IDs (excl. 2nd scans)
imgids=(
  1000 1001 1002 1003 1004 1005 1006 1007 1008 1009 1010
  1011 1012 1013 1014 1015 1017 1018 1019 1036 1101 1104
  1107 1110 1113 1116 1119 1122 1125
)

# scan with very different contrast in WM, exclude?
imgids=(${imgids[@]} 1128)

# ID of image used as reference for affine pre-alignment of all images
# when not set, use first image ID
refid="${imgids[0]}"

# list of image IDs used as targets/fixed images
# when this list is undefined or empty, all imgids are used
tgtids=("${imgids[@]}")

# list of image IDs used as source/moving images
# when this list is undefined or empty, all imgids are used
srcids=("${imgids[@]}")

# for evaluation of original ANTs SyN transformations of the MICCAI'12
# challenge, only a subset of transformed images and segmentations
# is available; no longer had the transformation files themselves;
# files provided by challenge participant Christian Ledig.
# - Andreas Schuh
if [ "$regid" = 'ants-syn-2012' ]; then
  srcids=(1000 1001 1002 1006 1007 1008 1009 1010 1011 1012 1013 1014 1015 1017 1036)
fi

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
rois=('msk' 'seg')

# change image data such that orientation matrix is the identity
#
# Results may differ for some tools when coordinate axes are swapped.
# This is the case for NiftyReg and MIRTK, for example. In case of MIRTK,
# the reoriented transformed source image gradient leads to slight
# differences in the numerical optimization. This can be avoided by
# changing the image data and header information before registration.
# Eventually, this should be addressed in the respective toolkits.
#
# The 'deorient' variable must be set to either an empty string to disable
# this step and use the original image data, or a string of arguments
# for the MIRTK flip-image command to perform the necessary operations.
#
# Note that the image header is updated accordingly. Therefore, a
# resulting transformation for world coordinates is the same with or
# without this deorientation step.
deorient="-x -y -yz"

# match histogram of reference image
nrmhst=true

# get file name prefix preceeding the image ID including subdirectories
get_prefix()
{
  if [ "$1" = 't1w' ]; then
    echo "N4/"
  elif [ "$1" = 'msk' ]; then
    echo "Masks/PINCRAM+Labels/"
  elif [ "$1" = 'seg' ]; then
    echo "Labels/"
  fi
}

# get file name suffix following the image ID including extension
get_suffix()
{
  if [ "$1" = 'seg' ]; then
    echo "_3_glm.nii.gz"
  else
    echo ".nii.gz"
  fi
}

# get background value or ID of foreground mask
get_bgvalue()
{
  echo 'msk'
}
