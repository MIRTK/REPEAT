## ALBERTs subset used for parameter optimization
##
## See 'alberts.sh' file in this directory for dataset information.

. "$(dirname "$BASH_SOURCE")/alberts.sh"
[ $? -eq 0 ] || error "Failed to load ALBERTs dataset configuration!"

tgtids=("${imgids[0]}" "${imgids[1]}")
srcids=("${tgtids[@]}")
