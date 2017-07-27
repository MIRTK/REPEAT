## Common settings sourced by executable scripts

topdir="$(cd "$(dirname "$BASH_SOURCE")/.." && pwd)"
. "$topdir/lib/utils.sh" || exit 1  # e.g., error() function

# ------------------------------------------------------------------------------
# global constants -- all directory paths relative to topdir

bindir="bin"
libdir="lib"
etcdir="etc"
vardir="var/cache"  # root directory for computed data
csvdir="var/table"  # summary tables of average quality measures

# absolute path of "mirtk" executable
mirtk="$topdir/$bindir/mirtk"

# when 'true', always compute all pairwise transformations even when
# registration method uses a symmetric energy function and thus the
# output of a given source to target registration may just be inverted
# to get the spatial mapping from the source to the target instead
allsym=false

# when 'true', evaluate mean inverse consistency error
evlice=true

# when 'true', evaluate mean transitivity error
evlmte=true

# when 'true', evaluate Jacobian determinants
evljac=true

# force overwriting previously generated files, include also jobs in batch
# description that have been run before, i.e., although output files already exist
force=false

# force re-generation of batch job description files, possibly excluding jobs
# whose output files already exist unless force=true as well
update=false

# HTCondor job execution environment
condor_getenv=true
condor_environment=

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
