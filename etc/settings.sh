## Common settings sourced by executable scripts

topdir="$(cd "$(dirname "$BASH_SOURCE")/.." && pwd)"

# ------------------------------------------------------------------------------
# global constants -- all directory paths relative to topdir

bindir="bin"
libdir="lib"
etcdir="etc"
cfgdir="$etcdir/params"  # CSV tables with sets of registration parameters
setdir="$etcdir/dataset" # dataset Shell configuration files
vardir="var/cache"       # root directory for computed data
csvdir="var/table"       # summary tables of average quality measures

# path of MIRTK's "mirtk" executable, either absolute or relative to topdir
# recommended: on Linux, download MIRTK AppImage to "$bindir/" and "chmod +x $bindir/mirtk"
mirtk="$bindir/mirtk"

# path of directory containing IRTK binaries, either absolute or relative to topdir
# recommended: create symbolic link "$bindir/irtk" with absolute path to actual installation
irtk="$bindir/irtk"

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
condor_requirements="Machine!=\"horatio.doc.ic.ac.uk\""

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

# get filename extension of transformation files
get_dofpre()
{
  local regpkg=${1/-*}
  if [ "$regpkg" = 'mirtk' -o "$regpkg" = 'irtk' ]; then
    echo ".dof.gz"
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
. "$topdir/$libdir/utils.sh" || exit 1  # e.g., error() function
