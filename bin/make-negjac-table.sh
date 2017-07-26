#!/bin/bash

print_help()
{
  error "usage: $(basename $BASH_SOURCE) <dataset> <regid> [<cfgid>...]"
}

# load settings and auxiliary functions
. "$(dirname "$BASH_SOURCE")/../etc/settings.sh" || exit 1
[ -n "$topdir" ] || error "etc/settings.sh: topdir not set"
[ -n "$etcdir" ] || error "etc/settings.sh: etcdir not set"
[ -n "$vardir" ] || error "etc/settings.sh: vardir not set"
[ -n "$csvdir" ] || error "etc/settings.sh: csvdir not set"

# change to top-level directory
run cd "$topdir"

# evaluation dataset
dataset="$1"; shift
[ -n "$dataset" ] || print_help
. "$etcdir/dataset-$dataset.sh"
[ ${#chns[@]}   -gt 0 ] || error "$etcdir/dataset-$dataset.sh: chns not set"
[ ${#imgids[@]} -gt 0 ] || error "$etcdir/dataset-$dataset.sh: imgids not set"
[ ${#tgtids[@]} -gt 0 ] || tgtids=("${imgids[@]}")
[ ${#srcids[@]} -gt 0 ] || srcids=("${imgids[@]}")

# registration method
regid="$1"; shift
[ -n "$regid" ] || print_help

cfgids=("$@")
[ ${#cfgids} -gt 0 ] || cfgids=($(get_cfgids "$regid"))
[ ${#cfgids} -gt 0 ] || error "etc/settings.sh: get_cfgids is empty for $regid"

chn="${chns[0]}"
bgvalue="$(get_bgvalue "$chn")"
if [ -n "$bgvalue" ]; then
  padding="-padding $bgvalue"
else
  padding=''
fi

for cfgid in "${cfgids[@]}"; do
  echo "Write no. of neg. Jacobians table for configuration $cfgid"

  dofdir="$vardir/$dataset/$regid/$cfgid/dof"
  imgdir="$vardir/$dataset/$regid/$cfgid/out/${chns[0]}"
  outdir="$csvdir/$dataset/$regid/$cfgid"

  makedir "$outdir"
  for tgtid in "${tgtids[@]}"; do
    outcsv="$outdir/$tgtid-negjac.csv"
    [ $force = true ] || [ ! -f "$outcsv" ] || continue
    echo "  Write no. of neg. Jacobians table for configuration $cfgid and tgtid=$tgtid"
    echo "srcid,n,pct" > "$outcsv"
    for srcid in "${srcids[@]}"; do
      if [ -f "$dofdir/$tgtid-$srcid.dof.gz" ]; then
        info=($("$mirtk" evaluate-jacobian "$imgdir/$srcid-$tgtid.nii.gz" "$dofdir/$tgtid-$srcid.dof.gz" $padding | grep 'Number of voxels with negative Jacobian determinant' | cut -d= -f2))
        if [ ${#info[@]} -eq 0 ]; then
          echo "$srcid,0,0" >> "$outcsv"
        elif [ ${#info[@]} -eq 2 ]; then
          echo "$srcid,${info[0]},${info[1]:1:-2}" >> "$outcsv"
        else
          error "Failed to determine no. of negative Jacobian determinants of '$dofdir/$tgtid-$srcid.dof.gz'"
        fi
      fi
    done
  done
done