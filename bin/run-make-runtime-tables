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

# evaluation dataset
dataset="$1"; shift
[ -n "$dataset" ] || print_help
. "$etcdir/dataset-$dataset.sh"
[ ${#imgids[@]} -gt 0 ] || error "$etcdir/dataset-$dataset.sh: imgids not set"
[ ${#tgtids[@]} -gt 0 ] || tgtids=("${imgids[@]}")
[ ${#srcids[@]} -gt 0 ] || srcids=("${imgids[@]}")

# registration method
regid="$1"; shift
[ -n "$regid" ] || print_help

cfgids=("$@")
[ ${#cfgids} -gt 0 ] || cfgids=($(get_cfgids "$regid"))
[ ${#cfgids} -gt 0 ] || error "etc/settings.sh: get_cfgids is empty for $regid"

for cfgid in "${cfgids[@]}"; do
  echo "Write runtime table for configuration $cfgid"

  logdir="$vardir/$dataset/$regid/$cfgid/log/register"
  outdir="$csvdir/$dataset/$regid/$cfgid"

  makedir "$outdir"
  for tgtid in "${tgtids[@]}"; do
    outcsv="$outdir/$tgtid-time.csv"
    [ $force = true ] || [ ! -f "$outcsv" ] || continue
    echo "  Write runtime table for configuration $cfgid and tgtid=$tgtid"
    echo "srcid,cpu_time,wall_time" > "$outcsv"
    for srcid in "${srcids[@]}"; do
      if [ -f "$logdir/$tgtid-$srcid.out" ]; then
        cols=($(grep 'CPU time is ' "$logdir/$tgtid-$srcid.out" | cut -d: -f2 | sed -r 's/CPU time is +([[:digit:]]+) h +([[:digit:]]+) min +([[:digit:]]+) sec/\1 \2 \3/'))
        cpu_time=$(/usr/bin/bc -l <<< "(${cols[0]} * 3600 + ${cols[1]} * 60 + ${cols[2]}) / 60.0")
        cols=($(grep 'Finished in ' "$logdir/$tgtid-$srcid.out" | cut -d: -f2 | sed -r 's/Finished in +([[:digit:]]+) h +([[:digit:]]+) min +([[:digit:]]+) sec/\1 \2 \3/'))
        wall_time=$(/usr/bin/bc -l <<< "(${cols[0]} * 3600 + ${cols[1]} * 60 + ${cols[2]}) / 60.0")
        printf "%s,%.2f,%.2f\n" "$srcid" $cpu_time $wall_time >> "$outcsv"
      fi
    done
  done
done