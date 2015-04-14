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

val regions = Measure.regions

val outSegPath      = Path.join(outDir, subDir, "${srcId}-${tgtId}" + segSuf).getAbsolutePath
val diceCsvPath     = Path.join(outDir, subDir, "DSC.csv").getAbsolutePath
val jaccCsvPath     = Path.join(outDir, subDir, "JSI.csv").getAbsolutePath
val meanDiceCsvPath = Path.join(outDir, "DSC.csv").getAbsolutePath
val meanJaccCsvPath = Path.join(outDir, "JSI.csv").getAbsolutePath

// Variables
val tgtId   = Val[Int]    // ID of target image
val srcId   = Val[Int]    // ID of source image
val tgtSeg  = Val[File]   // Target segmentation
val srcSeg  = Val[File]   // Source segmentation
val outDof  = Val[File]   // Transformation from target to source
val outSeg  = Val[File]   // Transformed source segmentation

val diceRow = Val[String] // Comma-separated Dice coefficients for each ROI of one segmentation
val diceAvg = Val[String] // Comma-separated mean Dice coefficients for each ROI over all segmentations
val diceCsv = Val[File]   // CSV file with Dice coefficients for each transformation

val jaccRow = Val[String] // Comma-separated Jaccard indices for each ROI of one segmentation
val jaccAvg = Val[String] // Comma-separated Jaccard indices for each ROI over all segmentations
val jaccCsv = Val[File]   // CSV file with Jaccard indices for each transformation

// Exploration task which iterates the image IDs and file paths
val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("ID", tgtId))
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("ID", srcId))
val sampling = {
  (tgtIdSampling x srcIdSampling).filter("tgtId != srcId") x
  (tgtSeg in SelectFileDomain(segDir, segPre + "${tgtId}" + segSuf)) x
  (srcSeg in SelectFileDomain(segDir, segPre + "${srcId}" + segSuf)) x
  (outDof in SelectFileDomain(dofDir, "${tgtId},${srcId}" + dofSuf))
}

val forEachDof = ExplorationTask(sampling) set (name := "forEachDof")

// Transform source segmentation
val warpBegin = EmptyTask() set (
    name    := "warpBegin",
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
    | val outSeg = new java.io.File(workDir, "$segPre" + srcId + "-" + tgtId + "$segSuf")
    |
    | IRTK.transform(src, outSeg, dofin = dof, interpolation = "NN", target = Some(tgt), matchInputType = true)
  """.stripMargin)

val warpTask = (configFile match {
    case Some(file) => _warpTask.addResource(file)
    case None => _warpTask
  }) set (
    name        := "warpTask",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += (tgtId, srcId),
    inputFiles  += (tgtSeg, segPre + "${tgtId}" + segSuf, symLnk),
    inputFiles  += (srcSeg, segPre + "${srcId}" + segSuf, symLnk),
    inputFiles  += (outDof, "${tgtId},${srcId}" + dofSuf, symLnk),
    outputFiles += (segPre + "${srcId}-${tgtId}" + segSuf, outSeg),
    outputs     += (tgtId, tgtSeg, srcId, srcSeg)
  ) hook CopyFileHook(outSeg, outSegPath) on parEnv

val warpSeg = warpBegin -- Skip(warpTask, "outSeg.lastModified() >= outDof.lastModified()")

// Compute overlap measures
val _calculateOverlap = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val segA    = new java.io.File(workDir, "$segPre" + tgtId + "$segSuf")
    | val segB    = new java.io.File(workDir, "$segPre" + srcId + "-" + tgtId + "$segSuf")
    | val regions = Measure.regions
    |
    | val overlap = Measure.overlap(segA, segB)
    | val dice    = Measure.dice(overlap)
    | val jaccard = Measure.jaccard(overlap)
    |
    | val diceRow = regions.map(region => dice   (region)).mkString(",")
    | val jaccRow = regions.map(region => jaccard(region)).mkString(",")
  """.stripMargin)

val calculateOverlap = (configFile match {
    case Some(file) => _calculateOverlap.addResource(file)
    case None => _calculateOverlap
  }) set (
    name        := "calculateOverlap",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, Measure.getClass),
    inputs      += (tgtId, srcId),
    inputFiles  += (tgtSeg, segPre + "${tgtId}" + segSuf),
    inputFiles  += (outSeg, segPre + "${srcId}-${tgtId}" + segSuf),
    outputs     += (tgtId, srcId, diceRow, jaccRow)
  ) hook (
    AppendToFileHook(diceCsvPath, "${tgtId},${srcId},${diceRow}\n"),
    AppendToFileHook(jaccCsvPath, "${tgtId},${srcId},${jaccRow}\n")
  ) on parEnv

// Compute mean overlap, one for each measure
val writeMeanDiceToCsv = ScalaTask(
  """
    | val diceAvg = diceRow.map( row => row.split(",").map(_.toDouble) ).transpose.map(_.sum / diceRow.size).mkString(",")
  """.stripMargin) set (
    name        := "writeMeanDiceToCsv",
    inputs      += diceRow.toArray,
    outputs     += diceAvg
  ) hook (
    AppendToFileHook(meanDiceCsvPath, subDir + ",${diceAvg}\n"),
    ToStringHook()
  )

val writeMeanJaccardToCsv = ScalaTask(
  """
     | val jaccAvg = jaccRow.map( row => row.split(",").map(_.toDouble) ).transpose.map(_.sum / jaccRow.size).mkString(",")
  """.stripMargin) set (
    name        := "writeMeanJaccardToCsv",
    inputs      += jaccRow.toArray,
    outputs     += jaccAvg
  ) hook (
    AppendToFileHook(meanJaccCsvPath, subDir + ",${jaccAvg}\n"),
    ToStringHook()
  )

// Run overlap evaluation pipeline for each transformation
CsvUtil.writeHeaderIfFileNotExists(new File(diceCsvPath),     Seq("target", "source") ++ regions)
CsvUtil.writeHeaderIfFileNotExists(new File(jaccCsvPath),     Seq("target", "source") ++ regions)
CsvUtil.writeHeaderIfFileNotExists(new File(meanDiceCsvPath), Seq("registration")     ++ regions)
CsvUtil.writeHeaderIfFileNotExists(new File(meanJaccCsvPath), Seq("registration")     ++ regions)

val exec = forEachDof -< warpSeg -- calculateOverlap >- (writeMeanDiceToCsv, writeMeanJaccardToCsv) start
exec.waitUntilEnded()
