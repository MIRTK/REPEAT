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
// Arguments
val subDir = getOrElse(args, 0, Constants.dofAffine)

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
val outDir = Constants.segODir

val dscValCsvPath = Path.join(outDir, subDir, "DSC.csv").getAbsolutePath
val dscGrpCsvPath = Path.join(outDir, subDir, "MeanDSC.csv").getAbsolutePath
val dscAvgCsvPath = Path.join(outDir, "DSC.csv").getAbsolutePath

val jsiValCsvPath = Path.join(outDir, subDir, "JSI.csv").getAbsolutePath
val jsiGrpCsvPath = Path.join(outDir, subDir, "MeanJSI.csv").getAbsolutePath
val jsiAvgCsvPath = Path.join(outDir, "JSI.csv").getAbsolutePath

// ---------------------------------------------------------------------------------------------------------------------
// Variables
val regName = Val[String]       // Name/ID of registration that computed the transformations (i.e., $subDir)
val tgtId   = Val[Int]          // ID of target image
val srcId   = Val[Int]          // ID of source image
val tgtSeg  = Val[File]         // Target segmentation
val srcSeg  = Val[File]         // Transformed source segmentation

val dscVal = Val[Array[Double]] // Dice similarity coefficient (DSC) for each label and segmentation
val dscGrp = Val[Array[Double]] // Mean DSC for each label group and segmentation
val dscAvg = Val[Array[Double]] // Mean DSC for each label group

val jsiVal = Val[Array[Double]] // Jaccard similarity index (JSI) for each label and segmentation
val jsiGrp = Val[Array[Double]] // Mean JSI for each label group and segmentation
val jsiAvg = Val[Array[Double]] // Mean JSI for each label group

// ---------------------------------------------------------------------------------------------------------------------
// Exploration task which iterates the image IDs and file paths
val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))

val sampling = {
  (tgtIdSampling x srcIdSampling).filter("tgtId != srcId") x
  (tgtSeg in SelectFileDomain(segDir, segPre + "${tgtId}" + segSuf)) x
  (srcSeg in SelectFileDomain(Path.join(outDir, subDir), segPre + "${srcId}-${tgtId}" + segSuf))
}

val forEachSeg = ExplorationTask(sampling) set (name := "forEachSeg")

// ---------------------------------------------------------------------------------------------------------------------
// Compute overlap measures
val evalOverlapTask = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val segA = new java.io.File(workDir, "$segPre" + tgtId + "$segSuf")
    | val segB = new java.io.File(workDir, "$segPre" + srcId + "-" + tgtId + "$segSuf")
    |
    | val stats = IRTK.labelStats(segA, segB, Some(Overlap.numbers.toSet))
    |
    | val dsc    = Overlap(stats, Overlap.DSC)
    | val dscVal = dsc.toArray
    | val dscGrp = dsc.getMeanValues
    |
    | val jsi    = Overlap(stats, Overlap.JSI)
    | val jsiVal = jsi.toArray
    | val jsiGrp = jsi.getMeanValues
    |
  """.stripMargin) set (
  name        := "evalOverlapTask",
  imports     += "com.andreasschuh.repeat._",
  usedClasses += (GlobalSettings.getClass, Overlap.getClass),
  inputs      += (tgtId, srcId),
  inputFiles  += (tgtSeg, segPre + "${tgtId}" + segSuf,          symLnk),
  inputFiles  += (srcSeg, segPre + "${srcId}-${tgtId}" + segSuf, symLnk),
  outputs     += (tgtId, srcId, dscVal, dscGrp, jsiVal, jsiGrp),
  taskBuilder => configFile.foreach(taskBuilder.addResource(_))
)

// Note: MUST be a capsule such that the actual task is only run once!
val evalOverlap = Capsule(evalOverlapTask) on parEnv

// ---------------------------------------------------------------------------------------------------------------------
// Write overlap measures for individual registrations to CSV files
val writeDscToCsv = EmptyTask() set (
    name    := "writeDscToCsv",
    inputs  += (tgtId, srcId, dscVal, dscGrp),
    outputs += (tgtId, srcId, dscVal, dscGrp)
  ) hook (
    AppendToCSVFileHook(dscValCsvPath, tgtId, srcId, dscVal) set (
      csvHeader := "Target ID,Source ID," + Overlap.numbers.mkString(","),
      singleRow := true
    ),
    AppendToCSVFileHook(dscGrpCsvPath, tgtId, srcId, dscGrp) set (
      csvHeader := "Target ID,Source ID," + Overlap.groups.mkString(","),
      singleRow := true
    )
  )

val writeJsiToCsv = EmptyTask() set (
    name    := "writeJsiToCsv",
    inputs  += (tgtId, srcId, jsiVal, jsiGrp),
    outputs += (tgtId, srcId, jsiVal, jsiGrp)
  ) hook (
    AppendToCSVFileHook(jsiValCsvPath, tgtId, srcId, jsiVal) set (
      csvHeader := "Target ID,Source ID," + Overlap.numbers.mkString(","),
      singleRow := true
    ),
    AppendToCSVFileHook(jsiGrpCsvPath, tgtId, srcId, jsiGrp) set (
      csvHeader := "Target ID,Source ID," + Overlap.groups.mkString(","),
      singleRow := true
    )
  )

// ---------------------------------------------------------------------------------------------------------------------
// Write mean overlap measures to summary CSV files
val writeMeanDscToCsv = ScalaTask("val dscAvg = dscGrp.transpose.map(_.sum / dscGrp.size)") set (
    name    := "writeMeanDscToCsv",
    inputs  += dscGrp.toArray,
    outputs += dscAvg
  ) hook (
    AppendToCSVFileHook(dscAvgCsvPath, regName, dscAvg) set (
      csvHeader := "Registration," + Overlap.groups.mkString(","),
      singleRow := true,
      inputs    += regName,
      regName   := subDir
    ),
    ToStringHook()
  )

val writeMeanJsiToCsv = ScalaTask("val jsiAvg = jsiGrp.transpose.map(_.sum / jsiGrp.size)") set (
    name    := "writeMeanJsiToCsv",
    inputs  += jsiGrp.toArray,
    outputs += jsiAvg
  ) hook (
    AppendToCSVFileHook(jsiAvgCsvPath, regName, jsiAvg) set (
      csvHeader := "Registration," + Overlap.groups.mkString(","),
      singleRow := true,
      inputs    += regName,
      regName   := subDir
    ),
    ToStringHook()
  )

// ---------------------------------------------------------------------------------------------------------------------
// Run workflow
Path.delete(dscValCsvPath)
Path.delete(dscGrpCsvPath)
Path.delete(jsiValCsvPath)
Path.delete(jsiGrpCsvPath)

val mole1 = forEachSeg  -< evalOverlap
val mole2 = evalOverlap -- (writeDscToCsv,     writeJsiToCsv)
val mole3 = evalOverlap >- (writeMeanDscToCsv, writeMeanJsiToCsv)

val exec = mole1 + mole2 + mole3 start
exec.waitUntilEnded()
