// =====================================================================================================================
// Registration Performance Assessment Tool (REPEAT)
// OpenMOLE script for registration overlap assessment
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
import com.andreasschuh.repeat._

// ---------------------------------------------------------------------------------------------------------------------
// Resources
val configFile = GlobalSettings().configFile

// ---------------------------------------------------------------------------------------------------------------------
// Environment on which to execute commands
val parEnv = Environment.short
val symLnk = Environment.symLnk

// ---------------------------------------------------------------------------------------------------------------------
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

// ---------------------------------------------------------------------------------------------------------------------
// Variables
val regName = Val[String]        // Name/ID of registration that computed the transformations (i.e., $subDir)
val tgtId   = Val[Int]           // ID of target image
val srcId   = Val[Int]           // ID of source image
val tgtSeg  = Val[File]          // Target segmentation
val srcSeg  = Val[File]          // Source segmentation
val outDof  = Val[File]          // Transformation from target to source
val outSeg  = Val[File]          // Transformed source segmentation

val diceRow = Val[Array[Double]] // Dice coefficient for each ROI of one segmentation
val diceAvg = Val[Array[Double]] // Mean Dice coefficient for each ROI over all segmentations
val diceCsv = Val[File]          // CSV file with Dice coefficients for each transformation

val jaccRow = Val[Array[Double]] // Jaccard index for each ROI of one segmentation
val jaccAvg = Val[Array[Double]] // Mean Jaccard index for each ROI over all segmentations
val jaccCsv = Val[File]          // CSV file with Jaccard indices for each transformation

// ---------------------------------------------------------------------------------------------------------------------
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

// ---------------------------------------------------------------------------------------------------------------------
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
    case None       => _warpTask
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

// ---------------------------------------------------------------------------------------------------------------------
// Compute overlap measures
val _measureOverlap = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val segA    = new java.io.File(workDir, "$segPre" + tgtId + "$segSuf")
    | val segB    = new java.io.File(workDir, "$segPre" + srcId + "-" + tgtId + "$segSuf")
    | val regions = Measure.regions
    |
    | val overlap = Measure.overlap(segA, segB)
    | val dice    = Measure.dice   (overlap)
    | val jaccard = Measure.jaccard(overlap)
    |
    | val diceRow = regions.map(region => dice   (region)).toArray
    | val jaccRow = regions.map(region => jaccard(region)).toArray
  """.stripMargin)

val measureOverlapTask = (configFile match {
    case Some(file) => _measureOverlap.addResource(file)
    case None       => _measureOverlap
  }) set (
    name        := "measureOverlap",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, Measure.getClass),
    inputs      += (tgtId, srcId),
    inputFiles  += (tgtSeg, segPre + "${tgtId}" + segSuf),
    inputFiles  += (outSeg, segPre + "${srcId}-${tgtId}" + segSuf),
    outputs     += (tgtId, srcId, diceRow, jaccRow)
  )

// Note: MUST be a capsule such that the actual task is only run once!
val measureOverlap = Capsule(measureOverlapTask) on parEnv

// ---------------------------------------------------------------------------------------------------------------------
// Write overlap measures for individual registrations to CSV files
val writeDiceToCsv = EmptyTask() set (
    name    := "writeDiceToCsv",
    inputs  += (tgtId, srcId, diceRow),
    outputs += (tgtId, srcId, diceRow)
  ) hook (
    AppendToCSVFileHook(diceCsvPath, tgtId, srcId, diceRow) set (
      csvHeader := "target,source," + regions.mkString(","),
      singleRow := true
    )
  )

val writeJaccToCsv = EmptyTask() set (
    name    := "writeJaccardToCsv",
    inputs  += (tgtId, srcId, jaccRow),
    outputs += (tgtId, srcId, jaccRow)
  ) hook (
    AppendToCSVFileHook(jaccCsvPath, tgtId, srcId, jaccRow) set (
      csvHeader := "target,source," + regions.mkString(","),
      singleRow := true
    )
  )

// ---------------------------------------------------------------------------------------------------------------------
// Write mean overlap measures to CSV files
val writeMeanDiceToCsv = ScalaTask("val diceAvg = diceRow.transpose.map(_.sum / diceRow.size)") set (
    name    := "writeMeanDiceToCsv",
    inputs  += diceRow.toArray,
    outputs += diceAvg
  ) hook (
    AppendToCSVFileHook(meanDiceCsvPath, regName, diceAvg) set (
      csvHeader := "registration," + regions.mkString(","),
      singleRow := true,
      inputs    += regName,
      regName   := subDir
    ),
    ToStringHook()
  )

val writeMeanJaccToCsv = ScalaTask("val jaccAvg = jaccRow.transpose.map(_.sum / jaccRow.size)") set (
    name    := "writeMeanJaccardToCsv",
    inputs  += jaccRow.toArray,
    outputs += jaccAvg
  ) hook (
    AppendToCSVFileHook(meanJaccCsvPath, regName, jaccAvg) set (
      csvHeader := "registration," + regions.mkString(","),
      singleRow := true,
      inputs    += regName,
      regName   := subDir
    ),
    ToStringHook()
  )

// ---------------------------------------------------------------------------------------------------------------------
// Run workflow
val diceCsvFile = new File(diceCsvPath)
if (diceCsvFile.exists) diceCsvFile.delete()

val jaccCsvFile = new File(jaccCsvPath)
if (jaccCsvFile.exists) jaccCsvFile.delete()

val mole1 = forEachDof     -< warpSeg -- measureOverlap -- (writeDiceToCsv, writeJaccToCsv)
val mole2 = measureOverlap >- (writeMeanDiceToCsv, writeMeanJaccToCsv)
val mole  = mole1 + mole2
val exec  = mole start
exec.waitUntilEnded()
