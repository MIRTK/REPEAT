## OASIS subset used for initial parameter exploration
##
## See 'oasis.sh' file in this directory for dataset information.

. "$(dirname "$BASH_SOURCE")/oasis.sh"
[ $? -eq 0 ] || error "Failed to load OASIS dataset configuration!"

tgtids=("${imgids[@]:0:4}")
srcids=("${imgids[@]}")
