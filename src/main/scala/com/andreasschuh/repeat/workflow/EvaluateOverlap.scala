/*
 * Registration Performance Assessment Tool (REPEAT)
 *
 * Copyright (C) 2015  Andreas Schuh
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
 */

package com.andreasschuh.repeat.workflow

import java.io.File
import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.plugin.domain.file._
import org.openmole.plugin.hook.display._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.task.scala._

import com.andreasschuh.repeat.core._


/**
 * Puzzle to assess segmentation overlap after registration
 */
object EvaluateOverlap {
  def apply(regDir: String) {
    val dscValCsvPath = FileUtil.join(Workspace.outDir, regDir, "DSC.csv").getAbsolutePath
    val dscGrpCsvPath = FileUtil.join(Workspace.outDir, regDir, "MeanDSC.csv").getAbsolutePath
    val dscAvgCsvPath = FileUtil.join(Workspace.outDir, "DSC.csv").getAbsolutePath
    val jsiValCsvPath = FileUtil.join(Workspace.outDir, regDir, "JSI.csv").getAbsolutePath
    val jsiGrpCsvPath = FileUtil.join(Workspace.outDir, regDir, "MeanJSI.csv").getAbsolutePath
    val jsiAvgCsvPath = FileUtil.join(Workspace.outDir, "JSI.csv").getAbsolutePath

    val go     = Val[Boolean]       // Trigger exploration after previous CSV files were deleted
    val regId  = Val[String]        // Name/ID of registration that computed the transformations (i.e., regDir)
    val tgtId  = Val[Int]           // ID of target image
    val srcId  = Val[Int]           // ID of source image
    val tgtSeg = Val[File]          // Target segmentation
    val srcSeg = Val[File]          // Transformed source segmentation

    val dscVal = Val[Array[Double]] // Dice similarity coefficient (DSC) for each label and segmentation
    val dscGrp = Val[Array[Double]] // Mean DSC for each label group and segmentation
    val dscAvg = Val[Array[Double]] // Mean DSC for each label group

    val jsiVal = Val[Array[Double]] // Jaccard similarity index (JSI) for each label and segmentation
    val jsiGrp = Val[Array[Double]] // Mean JSI for each label group and segmentation
    val jsiAvg = Val[Array[Double]] // Mean JSI for each label group

    def parEnv = Environment.short
    def symLnk = Environment.symLnk

    def imgCsv = Dataset.imgCsv
    def imgDir = Dataset.imgDir
    def imgPre = Dataset.imgPre
    def imgSuf = Dataset.imgSuf
    def segDir = Dataset.segDir
    def segPre = Dataset.segPre
    def segSuf = Dataset.segSuf

    val deleteOldFiles = ScalaTask(
        s"""
          | FileUtil.delete("$dscValCsvPath")
          | FileUtil.delete("$dscGrpCsvPath")
          | FileUtil.delete("$jsiValCsvPath")
          | FileUtil.delete("$jsiGrpCsvPath")
          | val go = true
        """.stripMargin) set (
        name    := "deleteOldFiles",
        imports += "com.andreasschuh.repeat.core.FileUtil"
      ) set (
        outputs += go
      )

    val forEachSeg = {
      val tgtIdSampling = CSVSampling(imgCsv) set (columns +=("Image ID", tgtId))
      val srcIdSampling = CSVSampling(imgCsv) set (columns +=("Image ID", srcId))
      val sampling = {
        (tgtIdSampling x srcIdSampling).filter("tgtId != srcId") x
        (tgtSeg in SelectFileDomain(segDir, segPre + "${tgtId}" + segSuf)) x
        (srcSeg in SelectFileDomain(FileUtil.join(Workspace.outDir, regDir), segPre + "${srcId}-${tgtId}" + segSuf))
      }
      ExplorationTask(sampling) set (
        name    := "forEachSeg",
        inputs  += go
      )
    }

    val evalOverlap = {
      val evalOverlapTask = ScalaTask(
        s"""
          | Config.dir(workDir)
          |
          | val segA = new java.io.File(workDir, "$segPre" + tgtId + "$segSuf")
          | val segB = new java.io.File(workDir, "$segPre" + srcId + "-" + tgtId + "$segSuf")
          |
          | val stats = IRTK.labelStats(segA, segB, Some(Overlap.labels.toSet))
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
        imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Overlap}",
        usedClasses += (Config.getClass, Overlap.getClass),
        inputs      += (tgtId, srcId),
        inputFiles  += (tgtSeg, segPre + "${tgtId}" + segSuf,symLnk),
        inputFiles  += (srcSeg, segPre + "${srcId}-${tgtId}" + segSuf, symLnk),
        outputs     += (tgtId, srcId, dscVal, dscGrp, jsiVal, jsiGrp),
        taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
      )
      // MUST be a capsule such that the actual task is only run once!
      Capsule(evalOverlapTask) on parEnv
    }

    val writeDscToCsv = EmptyTask() set (
      name    := "writeDscToCsv",
      inputs  += (tgtId, srcId, dscVal, dscGrp),
      outputs += (tgtId, srcId, dscVal, dscGrp)
    ) hook (
      AppendToCSVFileHook(dscValCsvPath, tgtId, srcId, dscVal) set (
        csvHeader := "Target ID,Source ID," + Overlap.labels.mkString(","),
        singleRow := true
      ), AppendToCSVFileHook(dscGrpCsvPath, tgtId,srcId, dscGrp) set (
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
        csvHeader := "Target ID,Source ID," + Overlap.labels.mkString(","),
        singleRow := true
      ),
      AppendToCSVFileHook(jsiGrpCsvPath, tgtId, srcId, jsiGrp) set (
        csvHeader :="Target ID,Source ID," + Overlap.groups.mkString(","),
        singleRow := true
      )
    )

    val writeMeanDscToCsv = ScalaTask("val dscAvg = dscGrp.transpose.map(_.sum / dscGrp.size)") set (
      name    := "writeMeanDscToCsv",
      inputs  += dscGrp.toArray,
      outputs += dscAvg
    ) hook (
      AppendToCSVFileHook(dscAvgCsvPath, regId, dscAvg) set (
        csvHeader := "Registration," + Overlap.groups.mkString(","),
        singleRow := true,
        inputs    += regId,
        regId     := regDir
      ),
      ToStringHook()
    )

    val writeMeanJsiToCsv = ScalaTask("val jsiAvg = jsiGrp.transpose.map(_.sum / jsiGrp.size)") set (
      name    := "writeMeanJsiToCsv",
      inputs  += jsiGrp.toArray,
      outputs += jsiAvg
    ) hook (
      AppendToCSVFileHook(jsiAvgCsvPath, regId, jsiAvg) set (
        csvHeader := "Registration," + Overlap.groups.mkString(","),
        singleRow := true,
        inputs    += regId,
        regId     := regDir
      ),
      ToStringHook()
    )

    val mole1 = deleteOldFiles -- forEachSeg -< evalOverlap
    val mole2 = evalOverlap -- (writeDscToCsv,     writeJsiToCsv)
    val mole3 = evalOverlap >- (writeMeanDscToCsv, writeMeanJsiToCsv)

    mole1 + mole2 + mole3
  }
}