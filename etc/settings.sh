## Common settings sourced by executable scripts

# ------------------------------------------------------------------------------
# global constants

etcdir="$(cd "$(dirname "$BASH_SOURCE")" && pwd)"
topdir="$(cd "${etcdir}/.." && pwd)"
bindir="$topdir/bin"
libdir="$topdir/lib"
mirtk="$bindir/mirtk"
force=false

# directories relative to topdir
vardir="var/cache"  # root directory for computed data
csvdir="var/table"  # summary tables of average quality measures

# HTCondor job execution environment
condor_getenv=true
condor_environment=

# when 'true', always compute all pairwise transformations even when
# registration method uses a symmetric energy function and thus the
# output of a given source to target registration may just be inverted
# to get the spatial mapping from the source to the target instead
allsym=false

# when 'true', evaluate mean inverse consistency error
evlice=true

# when 'true', evaluate mean transitivity error
evlmte=true

# ------------------------------------------------------------------------------
# registration method/modality/channel specific settings

# whether registration method uses symmetric energy function
is_sym()
{
  if [ "${regid/-sym-/}" != "$regid" ]; then
    echo true
  else
    echo false
  fi
}

# get evaluation measures for given modality/channel/contrast
get_measures()
{
  if [ "$1" = 't1w' -o "$1" = 't2w' ]; then
    echo "sdev entropy"
  elif [ "$1" = 'seg' ]; then
    echo "dsc entropy"
  fi
}

# ------------------------------------------------------------------------------
# auxiliary functions

. "$libdir/utils.sh" || exit 1
