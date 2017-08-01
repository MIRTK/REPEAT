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
  local dataset="$1"
  local regid="$2"
  if [ -n "$topdir" -a -n "$cfgdir" -a -n "$dataset" -a -n "$regid" ]; then
    local regcsv cfgpre
    local version="${regid/*-}"
    [ $(is_version "$version") = true ] || version=''
    for cfgpre in "$dataset/" ''; do
      if [ -f "$cfgdir/$cfgpre$regid.cfg" ]; then
        echo "$cfgpre$regid.cfg"
        break
      fi
      if [ -f "$cfgdir/$cfgpre$regid.csv" ]; then
        regcsv="$cfgdir/$cfgpre$regid.csv"
        break
      fi
      if [ -f "$cfgdir/$cfgpre$regid.gen" ]; then
        run "$cfgdir/$cfgpre$regid.gen"
        if [ -f "$cfgdir/$cfgpre$regid.csv" ]; then
          regcsv="$cfgdir/$cfgpre$regid.csv"
        else
          error "Script '$cfgdir/$cfgpre$regid.gen' expected to create file: $cfgdir/$cfgpre$regid.csv"
        fi
        break
      fi
      if [ -n "$version" ]; then
        local _regid=${regid%-*}
        if [ -f "$cfgdir/$cfgpre$_regid.cfg" ]; then
          echo "$cfgpre$_regid.cfg"
          break
        fi
        if [ -f "$cfgdir/$cfgpre$_regid.csv" ]; then
          regcsv="$cfgdir/$cfgpre$_regid.csv"
          break
        fi
        if [ -f "$cfgdir/$cfgpre$_regid.gen" ]; then
          run "$cfgdir/$cfgpre$_regid.gen"
          if [ -f "$cfgdir/$cfgpre$_regid.csv" ]; then
            regcsv="$cfgdir/$cfgpre$_regid.csv"
          else
            error "Script '$cfgdir/$cfgpre$_regid.gen' expected to create file: $cfgdir/$cfgpre$_regid.csv"
          fi
          break
        fi
      fi
    done
    if [ -n "$regcsv" ]; then
      tail -n +2 "$regcsv" | cut -d, -f1
    fi
  fi
}

# get background value of intensity image
get_bgvalue()
{
  if [ "$1" = 't1w' ] || [ "$1" = 't2w' ]; then
    echo 0
  fi
}

# get padded value in foreground extracted intensity image
# used when get_bgvalue returns an image mask instead of a value
get_padvalue()
{
  echo -1
}

# whether (background) value is a number
is_number()
{
  if [[ "$1" =~ ^[+-]?[0-9]+(\.[0-9]+)?$ ]] || [ "$1" = 'nan' -o "$1" = 'inf' -o "$1" = '+inf' -o "$1" = '-inf' ]; then
    echo true
  else
    echo false
  fi
}

# whether regid suffix is a software version number
is_version()
{
  if [[ "$1" =~ ^[0-9]+(\.[0-9]+)?(\.[0-9]+)([a-z]+)?$ ]] || [[ "$1" =~ ^rev[a-f0-9]+$ ]] || [ "$1" = 'dev' -o "$1" = 'master' -o "$1" = 'develop' -o "$1" = 'latest' ]; then
    echo true
  else
    echo false
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
  echo false
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
