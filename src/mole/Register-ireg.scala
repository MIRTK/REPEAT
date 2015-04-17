// =====================================================================================================================
// Registration Performance Assessment Tool (REPEAT)
// OpenMOLE script to perform pairwise deformable registrations using ireg (IRTK 3)
//
// Copyright (C) 2015  Andreas Schuh
//
//   This program is free software: you can redistribute it and/or modify
//   it under the terms of the GNU Affero General Public License as published by
//   the Free Software Foundation, either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU Affero General Public License for more details.
//
//   You should have received a copy of the GNU Affero General Public License
//   along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
// =====================================================================================================================

// ---------------------------------------------------------------------------------------------------------------------
// Import packages
import java.io.File
import com.andreasschuh.repeat.core._

// ---------------------------------------------------------------------------------------------------------------------
// Resources
val configFile = GlobalSettings().configFile

// ---------------------------------------------------------------------------------------------------------------------
// Environment on which to execute registrations
val parEnv = DefaultEnvironment.long
val symLnk = DefaultEnvironment.symLnk

// ---------------------------------------------------------------------------------------------------------------------
// Constants
val imgCsv = Constants.imgCsv
val imgDir = Constants.imgIDir
val imgPre = Constants.imgPre
val imgSuf = Constants.imgSuf
val segDir = Constants.segIDir
val segPre = Constants.segPre
val segSuf = Constants.segSuf
val dofSuf = Constants.dofSuf
val dofDir = Constants.dofDir
val dofIni = Constants.dofAffine
val logDir = Constants.logDir
val logSuf = Constants.logSuf

val dofName    = "ireg-${model.toLowerCase()}-im=${im.toLowerCase()}-ds=${ds}-be=${be}-bch=${bch}"
val outDofPath = Path.join(dofDir, dofName, "${tgtId},${srcId}" + dofSuf)
val regLogPath = Path.join(logDir, dofName, "${tgtId},${srcId}" + logSuf)
val outSegPath = Path.join(Constants.segODir, dofName, segPre + "${srcId}-${tgtId}" + segSuf)

// ---------------------------------------------------------------------------------------------------------------------
// Variables
val tgtId  = Val[Int]    // ID of target image
val srcId  = Val[Int]    // ID of source image
val tgtIm  = Val[File]   // Target image
val srcIm  = Val[File]   // Source image
val tgtSeg = Val[File]   // Target segmentation
val srcSeg = Val[File]   // Source segmentation
val outSeg = Val[File]   // Transformed source segmentation
val iniDof = Val[File]   // Initial guess/input transformation
val outDof = Val[File]   // Transformation from target to source
val regLog = Val[File]   // Registration output file

val model  = Val[String] // Transformation model
val im     = Val[String] // Integration method
val ds     = Val[Double] // Control point spacing
val be     = Val[Double] // Bending energy weight
val bch    = Val[Int]    // No. of BCH terms

// ---------------------------------------------------------------------------------------------------------------------
// Exploration task which iterates the image IDs and file paths
val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))

val imageSampling = {
  (tgtIdSampling x srcIdSampling).filter("tgtId != srcId") x
  (tgtIm  in SelectFileDomain(imgDir, imgPre + "${tgtId}" + imgSuf)) x
  (srcIm  in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)) x
  (tgtSeg in SelectFileDomain(segDir, segPre + "${tgtId}" + segSuf)) x
  (srcSeg in SelectFileDomain(segDir, segPre + "${srcId}" + segSuf)) x
  (iniDof in SelectFileDomain(Path.join(dofDir, dofIni), "${tgtId},${srcId}" + dofSuf))
}

val ffdSampling = {
  (model in List("FFD")) x
  (im    in List("FastSS")) x // unused
  (ds    in List(2.5)) x
  (be    in List(.0, .0001, .0005, .001, .005, .01, .05)) x
  (bch   in List(0)) // unused
}

val svffdSampling = {
  (model in List("SVFFD")) x
  (im    in List("FastSS", "SS", "RKE1", "RK4")) x
  (ds    in List(2.5)) x
  (be    in List(.0, .0001, .0005, .001, .005, .01, .05)) x
  (bch   in List(0, 4))
}

val paramSampling = ffdSampling :: svffdSampling
val sampling      = imageSampling x paramSampling

import org.openmole.core.workflow.data.Context
val numConfigurationsPerImagePair = paramSampling.build(Context())(new util.Random()).size
val numPairwiseRegistrations      = sampling     .build(Context())(new util.Random()).size

val forEachTuple = ExplorationTask(sampling) set (name := "forEachTuple")

// ---------------------------------------------------------------------------------------------------------------------
// Non-rigid registration
val iregBegin = EmptyTask() set (
  name    := "iregBegin",
  inputs  += (tgtId, tgtIm, srcId, srcIm, model, im, ds, be, bch, iniDof),
  outputs += (tgtId, tgtIm, srcId, srcIm, model, im, ds, be, bch, iniDof, outDof)
) source FileSource(outDofPath, outDof)

val iregTask = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val tgt    = new java.io.File(workDir, "$imgPre" + tgtId + "$imgSuf")
    | val src    = new java.io.File(workDir, "$imgPre" + tgtId + "$imgSuf")
    | val ini    = new java.io.File(workDir, tgtId + "," + srcId + "$dofSuf")
    | val outDof = new java.io.File(workDir, "result$dofSuf")
    | val regLog = new java.io.File(workDir, "output$logSuf")
    |
    | IRTK.ireg(tgt, src, Some(ini), outDof, Some(regLog),
    |   "Verbosity" -> 1,
    |   "No. of resolution levels" -> 4,
    |   "Maximum streak of rejected steps" -> 1,
    |   "Strict step length range" -> false,
    |   "Padding value" -> 0,
    |   "Transformation model" -> model,
    |   "Integration method" -> im,
    |   "Control point spacing" -> ds,
    |   "Bending energy weight" -> be,
    |   "No. of BCH terms" -> bch
    | )
  """.stripMargin) set (
  name        := "iregTask",
  imports     += "com.andreasschuh.repeat._",
  usedClasses += (GlobalSettings.getClass, IRTK.getClass),
  inputs      += (tgtId, srcId, model, im, ds, be, bch),
  inputFiles  += (tgtIm, imgPre + "${tgtId}" + imgSuf, symLnk),
  inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
  inputFiles  += (iniDof, "${tgtId},${srcId}" + dofSuf, symLnk),
  outputFiles += ("result" + dofSuf, outDof),
  outputFiles += ("output" + logSuf, regLog),
  outputs     += (tgtId, tgtIm, srcId, srcIm, model, im, ds, be, bch),
  taskBuilder => configFile.foreach(taskBuilder.addResource(_))
) hook (
  CopyFileHook(outDof, outDofPath),
  CopyFileHook(regLog, regLogPath)
) on parEnv

val regIms = iregBegin -- Skip(iregTask, "outDof.lastModified() > iniDof.lastModified()")

// ---------------------------------------------------------------------------------------------------------------------
// Transform source segmentation
val warpSegBegin = EmptyTask() set (
  name    := "outSegBegin",
  inputs  += (tgtId, tgtSeg, srcId, srcSeg, outDof),
  outputs += (tgtId, tgtSeg, srcId, srcSeg, outDof, outSeg)
) source FileSource(outSegPath, outSeg)

val warpSegTask = ScalaTask(
  s"""
     | GlobalSettings.setConfigDir(workDir)
     |
     | val tgt    = new java.io.File(workDir, "$segPre" + tgtId + "$segSuf")
     | val src    = new java.io.File(workDir, "$segPre" + srcId + "$segSuf")
     | val dof    = new java.io.File(workDir, tgtId + "," + srcId + "$dofSuf")
     | val outSeg = new java.io.File(workDir, "$segPre" + srcId + "-" + tgtId + "$segSuf")
     |
     | IRTK.transform(src, outSeg, dofin = dof, interpolation = "NN", target = Some(tgt), matchInputType = true)
  """.stripMargin) set (
  name        := "warpSegTask",
  imports     += "com.andreasschuh.repeat._",
  usedClasses += (GlobalSettings.getClass, IRTK.getClass),
  inputs      += (tgtId, srcId),
  inputFiles  += (tgtSeg, segPre + "${tgtId}" + segSuf, symLnk),
  inputFiles  += (srcSeg, segPre + "${srcId}" + segSuf, symLnk),
  inputFiles  += (outDof, "${tgtId},${srcId}" + dofSuf, symLnk),
  outputFiles += (segPre + "${srcId}-${tgtId}" + segSuf, outSeg),
  outputs     += (tgtId, tgtSeg, srcId, srcSeg),
  taskBuilder => configFile.foreach(taskBuilder.addResource(_))
) hook CopyFileHook(outSeg, outSegPath) on parEnv

val warpSeg = warpSegBegin -- Skip(warpSegTask, "outSeg.lastModified() >= outDof.lastModified()")

// ---------------------------------------------------------------------------------------------------------------------
// Run workflow
val exec = (forEachTuple -< regIms -- warpSeg) start
exec.waitUntilEnded()
