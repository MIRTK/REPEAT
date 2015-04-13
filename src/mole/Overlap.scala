// =============================================================================
// Project: Registration Performance Assessment Tool (REPEAT)
// Module:  OpenMOLE script for registration overlap assessment
//
// Copyright (c) 2015, Andreas Schuh.
// See LICENSE file for license information.
// =============================================================================

import java.io.File
import com.andreasschuh.repeat._

// Configuration file used by tasks upon execution
val configFile = GlobalSettings().configFile

// Environment on which to execute commands
val parEnv = Environment.short
val symLnk = Environment.symLnk

// Constants
val imgCsv = Constants.imgCsv
val imgDir = Constants.imgIDir
val imgPre = Constants.imgPre
val imgSuf = Constants.imgSuf
val segDir = Constants.segIDir
val segPre = Constants.segPre
val segSuf = Constants.segSuf
val dofDir = Path.join(Constants.dofDir, getOrElse(args, 0, "affine"))
val subDir = dofDir.getName
val dofSuf = Constants.dofSuf
val outDir = Constants.segODir
val logDir = Constants.logDir
val logSuf = Constants.logSuf

// Variables
val tgtId  = Val[Int]    // ID of target image
val srcId  = Val[Int]    // ID of source image
val tgtSeg = Val[File]   // Target segmentation
val srcSeg = Val[File]   // Source segmentation
val outDof = Val[File]   // Transformation from target to source
val outSeg = Val[File]   // Transformed source segmentation

// Exploration task which iterates the image IDs and file paths
val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("ID", tgtId))
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("ID", srcId))
val idsSampling   = (tgtIdSampling x srcIdSampling).filter("tgtId < srcId")
val sampling = {
  (tgtIdSampling x srcIdSampling).filter("tgtId < srcId") x
  (tgtSeg in SelectFileDomain(segDir, segPre + "${tgtId}" + segSuf)) x
  (srcSeg in SelectFileDomain(segDir, segPre + "${srcId}" + segSuf)) x
  (outDof in SelectFileDomain(dofDir, "${tgtId},${srcId}" + dofSuf))
}

val forEachDof = ExplorationTask(sampling)

// Transform source segmentation
val outSegPath = Path.join(outDir, subDir, "${srcId}-${tgtId}" + segSuf).getAbsolutePath

val warpBegin = EmptyTask() set (
    inputs  += (tgtId, tgtSeg, srcId, srcSeg, outDof),
    outputs += (tgtId, tgtSeg, srcId, srcSeg, outDof, outSeg)
  ) source FileSource(outSegPath, outSeg)

val _warpTask = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val tgt    = new java.io.File(workDir, "$segPre" + tgtId + "$segSuf")
    | val src    = new java.io.File(workDir, "$segPre" + srcId + "$segSuf")
    | val dof    = new java.io.File(workDir, tgtId + "," + srcId + "$dofSuf")
    | val outSeg = new java.io.File(workDir, "result$segSuf")
    |
    | IRTK.transform(src, outSeg, dofin = dof, interpolation = "NN", target = Some(tgt), matchInputType = true)
  """.stripMargin)

configFile match {
  case Some(file) => _warpTask.addResource(file)
  case None =>
}

val warpTask = _warpTask set (
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += (tgtId, srcId),
    inputFiles  += (tgtSeg, segPre + "${tgtId}" + segSuf, symLnk),
    inputFiles  += (srcSeg, segPre + "${srcId}" + segSuf, symLnk),
    inputFiles  += (outDof, "${tgtId},${srcId}" + dofSuf, symLnk),
    outputFiles += ("result" + segSuf, outSeg),
    outputs     += (tgtId, tgtSeg, srcId, srcSeg)
  ) hook CopyFileHook(outSeg, outSegPath) on parEnv

val warpMole = warpBegin -- Skip(warpTask, "outSeg.lastModified() >= outDof.lastModified()")

// Compute overlap measures


// Run overlap evaluation pipeline for each transformation
val exec = (forEachDof -< warpMole) start
exec.waitUntilEnded()
