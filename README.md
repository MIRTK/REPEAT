# REgistration PErformance Assessment Tools

The REgistration PErformance Assessment Tools (REPEAT) provide a common framework for
evaluating and comparing implementations of non-rigid image registration algorithms
with main focus on inter-subject registration of brain MR images.

The REPEAT scripts depend on the [Medical Image Registration ToolKit (MIRTK)](https://mirtk.github.io).


## Content

This folder contains scripts used to evaluate the quality of the spatial mappings
computed with different registration algorithms (and implementations thereof)
using an available brain image dataset, best with available manual annotations.

* `bin`
  - Main script(s) to perform evaluation.
* `etc`
  - Configuration shell files sourced by executable scripts.
  - Executable scripts to generate CSV files with registration parameters.
* `lib`
  - Shell modules defining auxiliary functions sourced by executable scripts.
  - Executable scripts to generate files for batch job submission.
* `opt`
  - Directory containing or linking to installations of registration tools.
  - On Linux, save (or link) downloaded MIRTK AppImage in this directory, e.g.,
    ```
    appimage=MIRTK-latest-x86_64-glibc2.14.AppImage
    mkdir -p opt && wget -O opt/mirtk https://bintray.com/schuhschuh/AppImages/download_file?file_path=$appimage
    ```
* `var/cache`
  - Output directory for (temporary) data files.
  - A subdirectory is created for each evaluation dataset, registration method,
    and parameter set, i.e., `regdir=var/cache/$dataset/$regid/$cfgid`. In case of
    the `affine` alignment, no `$cfgid` folder is used.
  - Batch job submission files are written to `$regdir/bin`.
    To submit jobs to the HTCondor system, use `condor_submit`, e.g.,
    `condor_submit $regdir/bin/register.condor`.
* `var/table`
  - Output directory for final average quality measures defined below.
  - The CSV files can be read using [Pandas](http://pandas.pydata.org/),
    and plots for figures can be created using [Seaborn](http://seaborn.pydata.org/)
    and [Matplotlib](https://matplotlib.org/).


## Affine alignment

The brain images are per-aligned to a selected reference image prior to the
deformable registrations. The used reference image is configured in the
`etc/settings.sh` file (`refid`). Images are then resampled on the lattice
of this reference image using the computed affine transformations.


## Deformable registration

For each registration method, register all images (`srcimgs`) to the selected target
images (`tgtimgs`). By default, all images are used as target. To reduce the number of
image pairs, only a subset may be used as target images, however. This is especially
useful in an initial parameter exploration to determine suitable ranges of parameters
which are then expected to perform similarly well for other target images.

In summary, for each dataset and registration method, the following steps are performed:

1. Generate CSV files with different sets of parameter values.
2. Perform pairwise registrations with each set of parameters,
   using each one of the specified images as target.
3. Resample affinely aligned images, including manual annotations,
   on the lattice of each target image.


## Qualitative measures

The qualitative measures that are evaluated for each input image (e.g., T2w intensity,
tissue segmentation, structural segmentation, and different probability maps) are
defined by the `get_measures()` function in `etc/settings.sh`. The set of measures
may be different for each dataset by (re-)defining this function in the dataset
specific configuration script (e.g., `etc/dataset/alberts.sh`).


### Runtime

The CPU time and real execution time (though depending on factors such as execution machine,
number of cores,...) is recorded for each registration method. The runtime for which different
methods achieve a similar qualitative result (according to below measures) can be compared to
see which registration tool obtains a result of such quality the fastest. Note that a decrease
in quality associated with, for example, a stronger regularisation can drastically reduce the
runtime. Because the dependence of the runtime on the regularity of the solution, runtime between
methods must be compared for qualitative similar results. Further, the runtimes to obtain the
best possible result for each method may be compared, but it has to be taken into account that
it may be reasonable to take longer if the result is considerably better. On the other hand,
if a method takes longer to obtain a less optimal result, it is clearly inferior to a method
with shorter runtime and qualitative better result.


### Segmentation overlap

For each registration method, and each target image:

* Compute DSC between target image segmentation with all other segmentations.
  - These tables can be read into a single Pandas DataFrame and plotted using Seaborn.
  - Calculate average overlap for a given target image, i.e., one value per target.
  - Calculate mean of average target overlaps, yielding mean DSC value for a method.
* Alternative segmentation overlap measures are also implemented by the used
  `evaluate-overlap` tool of MIRTK.


### Voxel-wise measures

Given the registered images, the following quantities are evaluated for each voxel
of each target image. An average of each measure within different ROIs is then
calculated using an either binary or probabilistic ROI image as weights. These
average measures are written to a CSV file, where columns correspond to the
different quantitative measures, and rows correspond to the different ROIs.
The mean, median, and maximum values can be used to summarise the results of
different registration methods.

* Inverse consistency error of obtained spatial mappings.
* Transitivity error of obtained spatial mappings.
* `var`: Variance of intensities at each target voxel.
* `stdev`: Standard deviation of intensities at each target voxel.
* `entropy`: Entropy of intensities or class labels at each target voxel.
* `gini`: Gini coefficient of intensities at each target voxel.
* `label-consistency`: Mean DSC of all pairs of labels at a target voxel.

Note that for each image separately, intensities are first z-score normalized,
and the resulting zero mean and unit variance intensities rescaled to the common
range [0, 100] such that absolute values of the computed quantities are comparable.
