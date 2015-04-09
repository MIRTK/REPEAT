// =============================================================================
// Project: Registration Performance Assessment Tool (REPEAT)
// Module:  OpenMOLE script for IRTK's ireg command
//
// Copyright (c) 2015, Andreas Schuh.
// See LICENSE file for license information.
// =============================================================================

import com.andreasschuh.repeat._

// Environment on which to execute registrations
val env = Workflow.env

// Constants
val imgCsv = Workflow.imgCsv
val imgDir = Workflow.imgIDir
val imgPre = Workflow.imgPre
val imgSuf = Workflow.imgSuf
val dofSuf = Workflow.dofSuf
val dofDir = Workflow.dofDir
val logDir = Workflow.logDir

// Variables
val tgtId  = Val[Int]    // ID of target image
val srcId  = Val[Int]    // ID of source image
val tgtIm  = Val[File]   // Target image
val srcIm  = Val[File]   // Source image
val iniDof = Val[File]   // Initial guess/input transformation
val outDof = Val[File]   // Output transformation
val regLog = Val[File]   // Registration output file

val model  = Val[String] // Transformation model
val im     = Val[String] // Integration method
val ds     = Val[Double] // Control point spacing
val be     = Val[Double] // Bending energy weight
val bch    = Val[Int]    // No. of BCH terms

// Exploration task which iterates the image IDs and file paths
val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("ID", tgtId))
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("ID", srcId))
val imageSampling = {
  (tgtIdSampling x srcIdSampling).filter("tgtId < srcId") +
  (tgtIm  in SelectFileDomain(imgDir, imgPre + "${tgtId}" + imgSuf)) +
  (srcIm  in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)) +
  (iniDof in SelectFileDomain(Path.join(dofDir, "affine"), "${tgtId},${srcId}" + dofSuf))
}

val ffdSampling = {
  (model in List("FFD")) x
  (im    in List("FastSS")) x // unused
  (ds    in List(2.5)) x
  (be    in List(.0, .0001, .0005, .001, .005, .01, .05)) x
  (bch   in List(0)) // unused
}
val paramSampling = ffdSampling

/*
val svffdSampling = {
  (model in List("SVFFD")) x
  (im    in List("FastSS", "SS", "RKE1", "RK4")) x
  (ds    in List(2.5)) x
  (be    in List(.0, .0001, .0005, .001, .005, .01, .05)) x
  (bch   in List(0, 4))
}
val paramSampling = svffdSampling
*/
val sampling      = imageSampling x paramSampling

import org.openmole.core.workflow.data.Context
val numConfigurationsPerImagePair = paramSampling.build(Context())(new util.Random()).size
val numPairwiseRegistrations      = sampling     .build(Context())(new util.Random()).size

val forEachTuple = ExplorationTask(imageSampling x paramSampling)

// Non-rigid registration mole
val iregBegin = EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, model, im, ds, be, bch, iniDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, model, im, ds, be, bch, iniDof, outDof)
  ) source FileSource(Path.join(dofDir, "ireg-${model.toLowerCase()}-im=${im.toLowerCase()}-ds=${ds}-be=${be}-bch=${bch}", "${tgtId},${srcId}" + dofSuf), outDof)

val iregTask = ScalaTask(
  """IRTK.ireg(tgtIm, srcIm, Some(iniDof), outDof, Some(regLog),
    |  "Verbosity" -> 1,
    |  "No. of threads" -> 1,
    |  "No. of resolution levels" -> 4,
    |  "Maximum streak of rejected steps" -> 1,
    |  "Strict step length range" -> false,
    |  "Background value" -> 0,
    |  "Padding value" -> 0,
    |  "Transformation model" -> model,
    |  "Integration method" -> im,
    |  "Control point spacing" -> ds,
    |  "Bending energy weight" -> be,
    |  "No. of BCH terms" -> bch
    |)
  """.stripMargin) set (
    imports     += "com.andreasschuh.repeat._",
    usedClasses += IRTK.getClass(),
    inputs      += (tgtId, tgtIm, srcId, srcIm, model, im, ds, be, bch, iniDof, outDof, regLog),
    outputs     += (tgtId, tgtIm, srcId, srcIm, model, im, ds, be, bch,         outDof)
  ) source FileSource(Path.join(logDir, "ireg-${model.toLowerCase()}-im=${im.toLowerCase()}-ds=${ds}-be=${be}-bch=${bch}", "${tgtId},${srcId}.log"), regLog)

val iregEnd = Capsule(EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, outDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, outDof)
  ))

val iregCond = "outDof.lastModified() < iniDof.lastModified()"
val iregMole = iregBegin -- (((iregTask on env) -- iregEnd) when iregCond, iregEnd when s"!($iregCond)")

// Run non-rigid registration pipeline for each pair of images
val exec = (forEachTuple -< iregMole) start
exec.waitUntilEnded()
