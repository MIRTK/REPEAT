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
import org.openmole.plugin.source.file._
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
  def apply(reg: Registration) = {

    import Dataset.{imgCsv, segPre, segSuf}
    import Workspace.dofPre

    val dscAvgCsvName = Overlap.summary.replace("${measure}", "DSC")
    val jsiAvgCsvName = Overlap.summary.replace("${measure}", "JSI")

    val dscValCsvPath = FileUtil.join(reg.resDir, "DSC.csv").getAbsolutePath
    val dscGrpCsvPath = FileUtil.join(reg.resDir, "MeanDSC.csv").getAbsolutePath
    val dscAvgCsvPath = FileUtil.join(reg.sumDir, dscAvgCsvName).getAbsolutePath
    val jsiValCsvPath = FileUtil.join(reg.resDir, "JSI.csv").getAbsolutePath
    val jsiGrpCsvPath = FileUtil.join(reg.resDir, "MeanJSI.csv").getAbsolutePath
    val jsiAvgCsvPath = FileUtil.join(reg.sumDir, jsiAvgCsvName).getAbsolutePath

    // -----------------------------------------------------------------------------------------------------------------
    // Variables
    val regId  = Val[String]               // Registration ID/name
    val parId  = Val[Int]                  // Parameter set ID (column index)
    val parVal = Val[Map[String, String]]  // Map from parameter name to value
    val tgtId  = Val[Int]                  // ID of target image
    val tgtSeg = Val[File]                 // Target segmentation image
    val srcId  = Val[Int]                  // ID of source image
    val outDof = Val[File]                 // Output transformation converted to IRTK format
    val outIm  = Val[File]                 // Deformed source image
    val outSeg = Val[File]                 // Deformed source segmentation

    val dscVal = Val[Array[Double]]        // Dice similarity coefficient (DSC) for each label and segmentation
    val dscGrp = Val[Array[Double]]        // Mean DSC for each label group and segmentation
    val dscAvg = Val[Array[Array[Double]]] // Mean DSC for each label group

    val jsiVal = Val[Array[Double]]        // Jaccard similarity index (JSI) for each label and segmentation
    val jsiGrp = Val[Array[Double]]        // Mean JSI for each label group and segmentation
    val jsiAvg = Val[Array[Array[Double]]] // Mean JSI for each label group

    // -----------------------------------------------------------------------------------------------------------------
    // Samplings
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal) zipWithIndex parId
    val tgtIdSampling = CSVSampling(imgCsv)
    tgtIdSampling.addColumn("Image ID", tgtId)
    val srcIdSampling = CSVSampling(imgCsv)
    srcIdSampling.addColumn("Image ID", srcId)
    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")

    val root    = reg.segDir.getPath.split(File.separatorChar).head
    val baseDir = new File(if (root.isEmpty) File.separator else root)
    val segPath = FileUtil.relativize(baseDir, reg.segDir)
    val dofPath = FileUtil.relativize(baseDir, reg.dofDir)

    val forEachImPairAndPar = ExplorationTask(
      imageSampling x paramSampling x
        (tgtSeg in SelectFileDomain(Workspace.segDir, segPre + "${tgtId}" + segSuf)) x
        (outSeg in SelectFileDomain(baseDir, segPath + File.separator + segPre + "${srcId}-${tgtId}" + segSuf)) x
        (outDof in SelectFileDomain(baseDir, dofPath + File.separator + dofPre + "${tgtId},${srcId}" + reg.dofSuf))
    ) set (name := s"${reg.id}-ForEachImPairAndPar", inputs += regId, outputs += regId, regId := reg.id)

    // -----------------------------------------------------------------------------------------------------------------
    // Backup/Move previous results
    val dscValCsv  = Val[File]
    val dscGrpCsv  = Val[File]
    val dscAvgCsv  = Val[File]
    val jsiValCsv  = Val[File]
    val jsiGrpCsv  = Val[File]
    val jsiAvgCsv  = Val[File]
    val parBakDone = Val[Boolean]
    val backupDone = Val[Boolean]

    val forEachPar = ExplorationTask(paramSampling) set (
        name    := s"${reg.id}-ForEachPar",
        inputs  += regId,
        outputs += regId,
        regId   := reg.id
      )

    val backupResults = if (reg.doBak) {
      val script =
        s"""
          | backup(s"$dscValCsvPath", true)
          | backup(s"$dscGrpCsvPath", true)
          | backup(s"$jsiValCsvPath", true)
          | backup(s"$jsiGrpCsvPath", true)
        """ + (if (Overlap.append) s"""
          | backup(s"$dscAvgCsvPath", true)
          | backup(s"$jsiAvgCsvPath", true)
        """ else "")
      ScalaTask(script.stripMargin + "val parBakDone = true") set (
          name        := s"${reg.id}-BackupResults",
          imports     += "com.andreasschuh.repeat.core.FileUtil.backup",
          usedClasses += FileUtil.getClass,
          inputs      += (regId, parId),
          outputs     += parBakDone,
          taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
        )
    } else {
      val script =
        s"""
          | delete(s"$dscValCsvPath")
          | delete(s"$dscGrpCsvPath")
          | delete(s"$jsiValCsvPath")
          | delete(s"$jsiGrpCsvPath")
        """ + (if (Overlap.append) s"""
          | delete(s"$jsiAvgCsvPath")
          | delete(s"$jsiAvgCsvPath")
        """ else "")
        ScalaTask(script.stripMargin + "val parBakDone = true") set (
          name        := s"${reg.id}-DeleteResults",
          imports     += "com.andreasschuh.repeat.core.FileUtil.delete",
          usedClasses += FileUtil.getClass,
          inputs      += (regId, parId),
          outputs     += parBakDone,
          taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
        )
    }

    val backupEnded = Capsule(ScalaTask("val backupDone = true") set (
        name    := s"${reg.id}-BackupResultsDone",
        inputs  += parBakDone.toArray,
        outputs += backupDone
      ))

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
          inputs      += (parId, tgtId, tgtSeg, srcId, outSeg),
          outputs     += (parId, tgtId, srcId, dscVal, dscGrp, jsiVal, jsiGrp),
          taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
        )
      Capsule(task, strainer = true)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Write overlaps of individual labels and label groups for each pair of images
    val writeDscToCsv = EmptyTask() set (
        name    := "writeDscToCsv",
        inputs  += (regId, parId, tgtId, srcId, dscVal, dscGrp),
        outputs += (regId, parId, tgtId, srcId, dscVal, dscGrp)
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
        inputs  += (regId, parId, tgtId, srcId, jsiVal, jsiGrp),
        outputs += (regId, parId, tgtId, srcId, jsiVal, jsiGrp)
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
    val header = Val[Array[String]]

    val writeMeanDscToCsv = ScalaTask(
      s"""
        | val regId         = input.regId.head
        | val dscAvgCsvFile = new java.io.File(s"$dscAvgCsvPath")
        | val dscAvg        = (dscGrp zip parId).groupBy(_._2).map {
        |   case (id, results) => id -> results.map(_._1).transpose.map(_.sum / results.size)
        | }.toArray.sortBy(_._1)
        | if (!dscAvgCsvFile.exists) {
        |   val res = new java.io.FileWriter(dscAvgCsvFile)
        |   try {
        |     if (${Overlap.append}) res.write("Registration,")
        |     res.write(header.mkString(",") + "\\n")
        |   } finally res.close()
        | }
        | val res = new java.io.FileWriter(dscAvgCsvFile, ${Overlap.append})
        | try {
        |   dscAvg.foreach {
        |     case (id, v) =>
        |       if (${Overlap.append}) res.write(s"$$regId-$$id,")
        |       res.write(v.mkString(",") + "\\n")
        |   }
        | } finally res.close()
      """.stripMargin) set (
        name        := s"${reg.id}-WriteMeanDscToCsv",
        inputs      += (regId.toArray, parId.toArray, dscGrp.toArray, header),
        header      := Overlap.groups.toArray,
        taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
      )

    val writeMeanJsiToCsv = ScalaTask(
      s"""
        | val regId         = input.regId.head
        | val jsiAvgCsvFile = new java.io.File(s"$jsiAvgCsvPath")
        | val jsiAvg        = (jsiGrp zip parId).groupBy(_._2).map {
        |   case (id, results) => id -> results.map(_._1).transpose.map(_.sum / results.size.toDouble)
        | }.toArray.sortBy(_._1)
        | if (!jsiAvgCsvFile.exists) {
        |   val res = new java.io.FileWriter(jsiAvgCsvFile)
        |   try {
        |     if (${Overlap.append}) res.write("Registration,")
        |     res.write(header.mkString(",") + "\\n")
        |   } finally res.close()
        | }
        | val res = new java.io.FileWriter(jsiAvgCsvFile, ${Overlap.append})
        | try {
        |   jsiAvg.foreach {
        |     case (id, v) =>
        |       if (${Overlap.append}) res.write(s"$$regId-$$id,")
        |       res.write(v.mkString(",") + "\\n")
        |   }
        | } finally res.close()
      """.stripMargin) set (
        name        := s"${reg.id}-WriteMeanJsiToCsv",
        inputs      += (regId.toArray, parId.toArray, jsiGrp.toArray, header),
        header      := Overlap.groups.toArray,
        taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
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

    val computeScores         = backupEnded -- forEachImPairAndPar -< (evalOverlap on Env.short)
    val writeIndividualScores = evalOverlap -- (writeDscToCsv, writeJsiToCsv)
    val writeMeanScores       = evalOverlap >- (writeMeanDscToCsv, writeMeanJsiToCsv)

    (forEachPar -< backupResults >- backupEnded) + computeScores + writeIndividualScores + writeMeanScores
  }
}