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

package com.andreasschuh.repeat.puzzle

import java.io.File

import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.plugin.domain.file._
import org.openmole.plugin.hook.display._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.task.scala._

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Assess segmentation overlap after registration
 */
object EvaluateOverlap {

  /**
   *
   * @param reg Registration whose label overlap should be evaluated
   */
  def apply(reg: Registration) {

    import Dataset.{imgCsv, segPre, segSuf}
    import Workspace.dofPre

    val dscValCsvPath = FileUtil.join(reg.resDir, "${parId}", "DSC.csv").getAbsolutePath
    val dscGrpCsvPath = FileUtil.join(reg.resDir, "${parId}", "MeanDSC.csv").getAbsolutePath
    val dscAvgCsvPath = FileUtil.join(reg.resDir, "DSC.csv").getAbsolutePath
    val jsiValCsvPath = FileUtil.join(reg.resDir, "${parId}", "JSI.csv").getAbsolutePath
    val jsiGrpCsvPath = FileUtil.join(reg.resDir, "${parId}", "MeanJSI.csv").getAbsolutePath
    val jsiAvgCsvPath = FileUtil.join(reg.resDir, "JSI.csv").getAbsolutePath

    // -----------------------------------------------------------------------------------------------------------------
    // Variables
    val regId  = Val[String] // Registration ID/name
    val parId  = Val[Int]    // Parameter set ID (column index)
    val parVal = Val[Map[String, String]] // Map from parameter name to value
    val tgtId  = Val[Int]    // ID of target image
    val tgtSeg = Val[File]   // Target segmentation image
    val srcId  = Val[Int]    // ID of source image
    val outDof = Val[File]   // Output transformation converted to IRTK format
    val outIm  = Val[File]   // Deformed source image
    val outSeg = Val[File]   // Deformed source segmentation

    val dscVal = Val[Array[Double]] // Dice similarity coefficient (DSC) for each label and segmentation
    val dscGrp = Val[Array[Double]] // Mean DSC for each label group and segmentation
    val dscAvg = Val[Array[Double]] // Mean DSC for each label group

    val jsiVal = Val[Array[Double]] // Jaccard similarity index (JSI) for each label and segmentation
    val jsiGrp = Val[Array[Double]] // Mean JSI for each label group and segmentation
    val jsiAvg = Val[Array[Double]] // Mean JSI for each label group

    // -----------------------------------------------------------------------------------------------------------------
    // Samplings
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal) zipWithIndex parId
    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")

    val forEachImPairAndPar = ExplorationTask(
      imageSampling x paramSampling x
        (tgtSeg in SelectFileDomain(Workspace.segDir, segPre + "${tgtId}" + segSuf)) x
        (outSeg in SelectFileDomain(reg.dofDir, segPre + "${srcId}-${tgtId}" + segSuf)) x
        (outDof in SelectFileDomain(reg.dofDir, dofPre + "${tgtId},${srcId}" + reg.dofSuf))
    ) set (name := "forEachImPairAndPar")

    // -----------------------------------------------------------------------------------------------------------------
    // Individual label overlaps
    val evalOverlap = {
      val task = ScalaTask(
        s"""
          | Config.dir(workDir)
          |
          | val stats = IRTK.labelStats(${tgtSeg.name}, ${outSeg.name}, Some(Overlap.labels.toSet))
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
          name        := s"${reg.id}-EvaluateOverlap",
          imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Overlap}",
          usedClasses += (Config.getClass, Overlap.getClass),
          inputs      += (tgtId, tgtSeg, srcId, outSeg),
          outputs     += (tgtId, srcId, dscVal, dscGrp, jsiVal, jsiGrp),
          taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
        )
      Capsule(task, strainer = true)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Write overlaps of individual labels and label groups for each pair of images
    val writeDscToCsv = EmptyTask() set (
        name    := "writeDscToCsv",
        inputs  += (tgtId, srcId, dscVal, dscGrp),
        outputs += (tgtId, srcId, dscVal, dscGrp)
      ) hook (
        AppendToCSVFileHook(dscValCsvPath, tgtId, srcId, dscVal) set (
          csvHeader := "Target ID,Source ID," + Overlap.labels.mkString(","),
          singleRow := true
        ),
        AppendToCSVFileHook(dscGrpCsvPath, tgtId,srcId, dscGrp) set (
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

    // -----------------------------------------------------------------------------------------------------------------
    // Write mean over all pairs of images
    val writeMeanDscToCsv = ScalaTask(
      """
        | val dscAvg = (dscGrp zip parId).groupBy(_._2).map {
        |   case (id, results) => id -> results.map(_._1).transpose.map(_.sum / results.size)
        | }.toArray.sortBy(_._1).map(_._2)
      """.stripMargin) set (
        name    := s"${reg.id}-WriteMeanDscToCsv",
        inputs  += (parId.toArray, dscGrp.toArray),
        outputs += dscAvg
      ) hook (
        AppendToCSVFileHook(dscAvgCsvPath, dscAvg) set (
          csvHeader := Overlap.groups.mkString(","),
          singleRow := false
        )
      )

    val writeMeanJsiToCsv = ScalaTask(
      """
       | val jscAvg = (jscGrp zip parId).groupBy(_._2).map {
       |   case (id, results) => id -> results.map(_._1).transpose.map(_.sum / results.size)
       | }.toArray.sortBy(_._1).map(_._2)
      """.stripMargin) set (
        name    := s"${reg.id}-WriteMeanJsiToCsv",
        inputs  += (parId.toArray, jsiGrp.toArray),
        outputs += jsiAvg
      ) hook (
        AppendToCSVFileHook(jsiAvgCsvPath, jsiAvg) set (
          csvHeader := Overlap.groups.mkString(","),
          singleRow := false
        )
      )

    // -----------------------------------------------------------------------------------------------------------------
    // TODO: Print overlap for each label group of registration with parameters which overall resulted in best mean overlap
    /*
    val printMaxDsc = ScalaTask() set (
        name    := s"${reg.id}-PrintMaxDsc",
        inputs  += dscAvg,
        outputs += dscMax
      ) hook ToStringHook()
    */

    val puzzle1 = forEachImPairAndPar -< (evalOverlap on Env.short)
    val puzzle2 = evalOverlap -- (writeDscToCsv, writeJsiToCsv)
    val puzzle3 = evalOverlap >- (writeMeanDscToCsv, writeMeanJsiToCsv)

    puzzle1 + puzzle2 + puzzle3
  }
}