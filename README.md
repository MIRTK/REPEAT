Registration Performance Assessment Tool
========================================

The REgistration PErformance Assessment Tool (REPEAT) provides a common framework for evaluating and comparing implementations of non-rigid image registration algorithms with the main focus on the inter-subject registration of brain MR images.

REPEAT uses [OpenMOLE](http://openmole.org/) to execute the workflows for the registration of two images and the computation of evaluation metrics used to assess the quality of the obtained transformations. This enables the parallel execution of many pairwise image registrations and the exploration of the parameter space in various high performance computing environments. OpenMOLE itself is based on the [GridScale](https://github.com/openmole/gridscale) library which supports many common distributed computing environments such as the Oracle Grid Engine (formerly known as SGE), SLURM, and Condor. It also enables the execution of workflow tasks on the [European Grid Infrastructure](http://www.egi.eu) or just locally on a single (multi-core) machine.

The software tools included in this project are implemented in C++ and Scala, a modern incarnation of Java which combines strong typesafety with the ease and expressiveness of other scripting languages such as Ruby and Python. They are executed by the JVM which is available for all major operating systems.

Data
====


Metrics
=======