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

val regions   = Measure.regions
val csvHeader = "target,source," + regions.mkString(",")

val outSegPath  = Path.join(outDir, subDir, "${srcId}-${tgtId}" + segSuf).getAbsolutePath
val diceCsvPath = Path.join(outDir, subDir, "dice.csv").getAbsolutePath
val jaccCsvPath = Path.join(outDir, subDir, "jaccard.csv").getAbsolutePath

// Variables
val tgtId   = Val[Int]    // ID of target image
val srcId   = Val[Int]    // ID of source image
val tgtSeg  = Val[File]   // Target segmentation
val srcSeg  = Val[File]   // Source segmentation
val outDof  = Val[File]   // Transformation from target to source
val outSeg  = Val[File]   // Transformed source segmentation
val diceCsv = Val[File]   // CSV file with Dice coefficients for each transformation
val diceRow = Val[String] // Comma-separated Dice coefficients for each ROI of one segmentation
val jaccCsv = Val[File]   // CSV file with overlap measures for each transformation
val jaccRow = Val[String] // Comma-separated Jaccard indices for each ROI of one segmentation

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
//val warpSeg = warpBegin -- (warpTask, "outSeg.lastModified() >= outDof.lastModified()")
//val warpSeg = warpBegin -- warpTask
//val warpSeg = warpBegin

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
    | val diceRow = tgtId + "," + srcId + "," + regions.map(region => dice   (region)).mkString(",")
    | val jaccRow = tgtId + "," + srcId + "," + regions.map(region => jaccard(region)).mkString(",")
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
    outputs     += (diceRow, jaccRow)
  ) on parEnv

// Write overlap to CSV file, one for each measure
val writeDiceToCsv = ScalaTask(
  s"""
    | val diceCsv = new java.io.File(workDir, "dice.csv")
    | val writer  = new java.io.FileWriter(diceCsv, false)
    | try {
    |   writer.write("$csvHeader\\n")
    |   diceRow.sorted.foreach(row => writer.write(row + '\\n'))
    | }
    | finally writer.close()
  """.stripMargin) set (
    name        := "writeDiceToCsv",
    inputs      += diceRow.toArray,
    outputFiles += ("dice.csv", diceCsv)
  ) hook CopyFileHook(diceCsv, diceCsvPath)

val writeJaccardToCsv = ScalaTask(
  s"""
    | val jaccCsv = new java.io.File(workDir, "jaccard.csv")
    | val writer  = new java.io.FileWriter(jaccCsv, false)
    | try {
    |   writer.write("$csvHeader\\n")
    |   jaccRow.sorted.foreach(row => writer.write(row + '\\n'))
    | }
    | finally writer.close()
  """.stripMargin) set (
    name        := "writeJaccardToCsv",
    inputs      += jaccRow.toArray,
    outputFiles += ("jaccard.csv", jaccCsv)
  ) hook CopyFileHook(jaccCsv, jaccCsvPath)

// Report average overlap
val avgDice = ScalaTask(
  s"""
    | val num = diceRow.size
    | val sum = diceRow.map( row => row.split(',').drop(2).map(_.toDouble) ).transpose.map(_.sum)
    | val roi = "$csvHeader".split(',').drop(2)
    | for (i <- 0 until roi.size) {
    |   println(f"Average Dice coefficient for $${roi(i)} region is $${100 * sum(i) / num}%.2f%%")
    | }
  """.stripMargin) set (name := "avgDice", inputs += diceRow.toArray)

val avgJaccard = ScalaTask(
  s"""
     | val num = jaccRow.size
     | val sum = jaccRow.map( row => row.split(',').drop(2).map(_.toDouble) ).transpose.map(_.sum)
     | val roi = "$csvHeader".split(',').drop(2)
     | for (i <- 0 until roi.size) {
     |   println(f"Average Jaccard index for $${roi(i)} region is $${100 * sum(i) / num}%.2f%%")
     | }
  """.stripMargin) set (name := "avgJaccard", inputs += jaccRow.toArray)

// Run overlap evaluation pipeline for each transformation
val mole = forEachDof -< warpSeg -- calculateOverlap >- (writeDiceToCsv, writeJaccardToCsv, avgDice, avgJaccard) toMole

println(mole.capsules)
println(mole.transitions)

val exec = mole start
exec.waitUntilEnded()
