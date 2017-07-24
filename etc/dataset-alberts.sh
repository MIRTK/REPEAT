## ALBERTs dataset information
##
## This file is sourced by the rename-images and link-images
## executable scripts. Use only one of these scripts to set
## up this dataset after making sure that the information
## given below reflects the file hierarchy and names of the
## respecively downloaded dataset.
##
## The original ALBERTs images and manual annotations are available at:
## http://brain-development.org/brain-atlases/neonatal-brain-atlas-albert/
##
## It is recommended, however, to use the modified images used
## as atlases by the MIRTK Draw-EM brain segmentation package:
## https://www.doc.ic.ac.uk/~am411/atlases-DrawEM.html

# common top level directory of dataset images
imgdir="$HOME/Atlases/ALBERTs/DrawEM"

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
# in case of a hard segmentation, a probabilistic segmentation is created for
# for each target image, resulting in one average value for each label
rois=('seg' 'cgm')

# ID of image used as reference for affine pre-alignment of all images
# when not set, use first image ID
refid="${imgids[0]}"

# list of image IDs used as targets
# when this list is undefined or empty, use all imgids
tgtids=("${imgids[@]:0:5}")

# list of image IDs used as source/moving images
# when this list is undefined or empty, use all imgids
srcids=("${imgids[@]}")

# get file name prefix preceeding the image ID including subdirectories
get_prefix()
{
  if [ $1 = 't1w' ]; then
    echo "T1/ALBERT_"
  elif [ $1 = 't2w' ]; then
    echo "T2/ALBERT_"
  elif [ $1 = 'seg' ]; then
    echo "segmentations-v3/ALBERT_"
  elif [ $1 = 'cgm' ]; then
    echo "gm-posteriors-v3/ALBERT_"
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
  if [ $1 = 't1w' ] || [ $1 = 't2w' ]; then
    echo "0"
  fi
}

# whether modality/channel is a binary mask
is_mask()
{
  echo false
}

# whether modality/channel is a hard segmentation (label image)
is_seg()
{
  if [ $1 = 'seg' ]; then
    echo true
  else
    echo false
  fi
}

# whether modality/channel is a probabilistic segmentation
is_prob()
{
  if [ $1 = 'cgm' ]; then
    echo true
  else
    echo false
  fi
}
