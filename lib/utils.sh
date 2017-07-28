## Auxiliary functions

# print error to STDERR and exit
error()
{
  echo -e "$1" 1>&2
  exit 1
}

# Set variable in scope of caller
upvar()
{
  if unset -v "$1"; then       # Unset & validate varname
    if [ $# -eq 2 ]; then
      eval $1=\"\$2\"          # Return single value
    else
      eval $1=\(\"\${@:2}\"\)  # Return array
    fi
  fi
}

# run command and exit if it fails
run()
{
  echo "> $@"
  "$@" || exit 1
}


# make directory if it does not exist
makedir()
{
  for path in "$@"; do
    [ -d "$path" ] || run mkdir -p "$path"
  done
}

# get relative path
relpath()
{
  python -c "import os,sys;print(os.path.relpath(*(sys.argv[1:])))" "$@";
}

# get IDs of registration configurations (i.e., parameter sets)
# for a given registration tool that are being evaluated
get_cfgids()
{
  local regid="$1"
  if [ -n "$topdir" -a -n "$cfgdir" -a -n "$regid" -a -f "$topdir/$cfgdir/$regid.csv" ]; then
    tail -n +2 "$topdir/$cfgdir/$regid.csv" | cut -d, -f1
  fi
}

# whether a given evaluation measure is a segmentation overlap measure
is_overlap_measure()
{
  if [ "$1" = 'dsc' -o "$1" = 'jsc' -o "$1" = 'acc' -o "$1" = 'tpr' -o \
       "$1" = 'tnr' -o "$1" = 'ppv' -o "$1" = 'npv' -o "$1" = 'fpr' -o \
       "$1" = 'fdr' -o "$1" = 'fnr' -o "$1" = 'mcc' -o "$1" = 'bm'  -o \
       "$1" = 'mk' ]; then
    echo true
  else
    echo false
  fi
}
