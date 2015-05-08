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
import org.openmole.plugin.grouping.batch._
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

    val dscEnabled       = Overlap.measures contains Overlap.DSC
    val dscAvgCsvName    = Overlap.summary.replace("${measure}", "DSC")
    val dscValCsvPath    = FileUtil.join(reg.resDir, "DSC_Label.csv").getAbsolutePath
    val dscGrpAvgCsvPath = FileUtil.join(reg.resDir, "DSC_Mean.csv").getAbsolutePath
    val dscGrpStdCsvPath = FileUtil.join(reg.resDir, "DSC_Sigma.csv").getAbsolutePath
    val dscAvgCsvPath    = FileUtil.join(reg.sumDir, dscAvgCsvName).getAbsolutePath

    val jsiEnabled       = Overlap.measures contains Overlap.JSI
    val jsiAvgCsvName    = Overlap.summary.replace("${measure}", "JSI")
    val jsiValCsvPath    = FileUtil.join(reg.resDir, "JSI_Label.csv").getAbsolutePath
    val jsiGrpAvgCsvPath = FileUtil.join(reg.resDir, "JSI_Mean.csv").getAbsolutePath
    val jsiGrpStdCsvPath = FileUtil.join(reg.resDir, "JSI_Sigma.csv").getAbsolutePath
    val jsiAvgCsvPath    = FileUtil.join(reg.sumDir, jsiAvgCsvName).getAbsolutePath

    // -----------------------------------------------------------------------------------------------------------------
    // Variables
    val regId  = Val[String]               // Registration ID/name
    val parId  = Val[Int]                  // Parameter set ID (column index)
    val parVal = Val[Map[String, String]]  // Map from parameter name to value
    val tgtId  = Val[Int]                  // ID of target image
    val tgtSeg = Val[File]                 // Target segmentation image
    val srcId  = Val[Int]                  // ID of source image
    val outDof = Val[File]                 // Output transformation converted to IRTK format
    val outSeg = Val[File]                 // Deformed source segmentation

    val dscVal    = Val[Array[Double]]     // Dice similarity coefficient (DSC) for each label and segmentation
    val dscGrpAvg = Val[Array[Double]]     // Mean DSC for each label group and segmentation
    val dscGrpStd = Val[Array[Double]]     // Standard deviation of DSC for each label group and segmentation

    val jsiVal    = Val[Array[Double]]     // Jaccard similarity index (JSI) for each label and segmentation
    val jsiGrpAvg = Val[Array[Double]]     // Mean JSI for each label group and segmentation
    val jsiGrpStd = Val[Array[Double]]     // Standard deviation of JSI for each label group and segmentation

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

    val forEachPar = ExplorationTask(paramSampling) set (
        name    := s"${reg.id}-ForEachPar",
        inputs  += regId,
        outputs += regId,
        regId   := reg.id
      )

    val incParId = ScalaTask("val parId = input.parId + 1") set (
        name    := "incParId",
        inputs  += parId,
        outputs += parId
      )

    val forEachImPair = ExplorationTask(
      imageSampling x
        (tgtSeg in SelectFileDomain(Workspace.segDir, segPre + "${tgtId}" + segSuf)) x
        (outSeg in SelectFileDomain(baseDir, segPath + File.separator + segPre + "${srcId}-${tgtId}" + segSuf)) x
        (outDof in SelectFileDomain(baseDir, dofPath + File.separator + dofPre + "${tgtId},${srcId}" + reg.dofSuf))
    ) set (
      name    := s"${reg.id}-ForEachImPairAndPar",
      inputs  += (regId, parId),
      outputs += (regId, parId),
      regId   := reg.id
    )

    // -----------------------------------------------------------------------------------------------------------------
    // Backup/Move previous results
    val parBakDone = Val[Boolean]
    val backupDone = Val[Boolean]

    val backupResults = if (reg.doBak) {
      val script =
        s"""
          | if ($dscEnabled) {
          |   backup(s"$dscValCsvPath",    true)
          |   backup(s"$dscGrpAvgCsvPath", true)
          |   backup(s"$dscGrpStdCsvPath", true)
          | }
          | if ($jsiEnabled) {
          |   backup(s"$jsiValCsvPath",    true)
          |   backup(s"$jsiGrpAvgCsvPath", true)
          |   backup(s"$jsiGrpStdCsvPath", true)
          | }
        """ + (if (Overlap.append) s"""
          | if ($dscEnabled) backup(s"$dscAvgCsvPath", true)
          | if ($jsiEnabled) backup(s"$jsiAvgCsvPath", true)
        """ else "")
      ScalaTask(script.stripMargin + "val parBakDone = true") set (
          name        := s"${reg.id}-BackupResults",
          imports     += "com.andreasschuh.repeat.core.FileUtil.backup",
          usedClasses += FileUtil.getClass,
          inputs      += (regId, parId),
          outputs     += parBakDone
        )
    } else {
      val script =
        s"""
          | if ($dscEnabled) {
          |   delete(s"$dscValCsvPath")
          |   delete(s"$dscGrpAvgCsvPath")
          |   delete(s"$dscGrpStdCsvPath")
          | }
          | if ($jsiEnabled) {
          |   delete(s"$jsiValCsvPath")
          |   delete(s"$jsiGrpAvgCsvPath")
          |   delete(s"$jsiGrpStdCsvPath")
          | }
        """ + (if (Overlap.append) s"""
          | if ($dscEnabled) delete(s"$jsiAvgCsvPath")
          | if ($jsiEnabled) delete(s"$jsiAvgCsvPath")
        """ else "")
        ScalaTask(script.stripMargin + "val parBakDone = true") set (
          name        := s"${reg.id}-DeleteResults",
          imports     += "com.andreasschuh.repeat.core.FileUtil.delete",
          usedClasses += FileUtil.getClass,
          inputs      += (regId, parId),
          outputs     += parBakDone
        )
    }

    val backupEnded = Capsule(ScalaTask("val backupDone = true") set (
        name    := s"${reg.id}-BackupResultsDone",
        inputs  += parBakDone.toArray,
        outputs += backupDone
      ))

    // -----------------------------------------------------------------------------------------------------------------
    // Individual label overlaps
    val evalOverlap = Capsule(ScalaTask(
      s"""
        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
        |
        | val stats = IRTK.labelStats(${tgtSeg.name}, ${outSeg.name}, Some(Overlap.labels.toSet))
        |
        | val dsc       = Overlap(stats, Overlap.DSC)
        | val dscVal    = dsc.toArray
        | val dscGrpAvg = dsc.getMeanValues
        | val dscGrpStd = dsc.getSigmaValues
        |
        | val jsi       = Overlap(stats, Overlap.JSI)
        | val jsiVal    = jsi.toArray
        | val jsiGrpAvg = jsi.getMeanValues
        | val jsiGrpStd = jsi.getSigmaValues
        |
      """.stripMargin) set (
        name        := s"${reg.id}-EvaluateOverlap",
        imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Overlap}",
        usedClasses += (Config.getClass, IRTK.getClass, Overlap.getClass),
        inputs      += (regId, parId, tgtId, srcId),
        inputFiles  += (tgtSeg, "target" + segSuf, link = Workspace.shared),
        inputFiles  += (outSeg, "labels" + segSuf, link = Workspace.shared),
        outputs     += (regId, parId, tgtId, srcId, dscVal, dscGrpAvg, dscGrpStd, jsiVal, jsiGrpAvg, jsiGrpStd)
      ))

    // -----------------------------------------------------------------------------------------------------------------
    // Write overlaps of individual labels and label groups for each pair of images
    val writeDscVal = EmptyTask() set (
        name    := "writeDscVal",
        inputs  += (regId, parId, tgtId, srcId, dscVal),
        outputs += (regId, parId, tgtId, srcId, dscVal)
      ) hook (
        AppendToCSVFileHook(dscValCsvPath, tgtId, srcId, dscVal) set (
          csvHeader := "Target ID,Source ID," + Overlap.labels.mkString(","),
          singleRow := true
        )
      )

    val writeDscAvg = EmptyTask() set (
        name    := "writeDscAvg",
        inputs  += (regId, parId, tgtId, srcId, dscGrpAvg),
        outputs += (regId, parId, tgtId, srcId, dscGrpAvg)
      ) hook (
        AppendToCSVFileHook(dscGrpAvgCsvPath, tgtId, srcId, dscGrpAvg) set (
          csvHeader := "Target ID,Source ID," + Overlap.groups.mkString(","),
          singleRow := true
        )
      )

    val writeDscStd = EmptyTask() set (
        name    := "writeDscStd",
        inputs  += (regId, parId, tgtId, srcId, dscGrpStd),
        outputs += (regId, parId, tgtId, srcId, dscGrpStd)
      ) hook (
        AppendToCSVFileHook(dscGrpStdCsvPath, tgtId,srcId, dscGrpStd) set (
          csvHeader := "Target ID,Source ID," + Overlap.groups.mkString(","),
          singleRow := true
        )
      )

    val writeJsiVal = EmptyTask() set (
        name    := "writeJsiVal",
        inputs  += (regId, parId, tgtId, srcId, jsiVal),
        outputs += (regId, parId, tgtId, srcId, jsiVal)
      ) hook (
        AppendToCSVFileHook(jsiValCsvPath, tgtId, srcId, jsiVal) set (
          csvHeader := "Target ID,Source ID," + Overlap.labels.mkString(","),
          singleRow := true
        )
      )

    val writeJsiAvg = EmptyTask() set (
        name    := "writeJsiAvg",
        inputs  += (regId, parId, tgtId, srcId, jsiGrpAvg),
        outputs += (regId, parId, tgtId, srcId, jsiGrpAvg)
      ) hook (
        AppendToCSVFileHook(jsiGrpAvgCsvPath, tgtId, srcId, jsiGrpAvg) set (
          csvHeader := "Target ID,Source ID," + Overlap.groups.mkString(","),
          singleRow := true
        )
      )

    val writeJsiStd = EmptyTask() set (
      name    := "writeJsiStd",
      inputs  += (regId, parId, tgtId, srcId, jsiGrpStd),
      outputs += (regId, parId, tgtId, srcId, jsiGrpStd)
      ) hook (
        AppendToCSVFileHook(jsiGrpStdCsvPath, tgtId, srcId, jsiGrpStd) set (
          csvHeader := "Target ID,Source ID," + Overlap.groups.mkString(","),
          singleRow := true
        )
      )

    // -----------------------------------------------------------------------------------------------------------------
    // Write mean over all pairs of images
    val header = Val[Array[String]]

    val writeMeanDsc = ScalaTask(
      s"""
        | val regId = input.regId.head
        | val dscAvgCsvFile = new java.io.File(s"$dscAvgCsvPath")
        | val dscAvg = (dscGrpAvg zip parId).groupBy(_._2).map {
        |   case (id, results) => id -> results.map(_._1).transpose.map(_.sum / results.size)
        | }.toArray.sortBy(_._1)
        | if (!dscAvgCsvFile.exists) {
        |   val res = new java.io.FileWriter(dscAvgCsvFile)
        |   try {
        |     if (${Overlap.append}) res.write("Registration,Parameters,")
        |     res.write(header.mkString(",") + "\\n")
        |   }
        |   catch {
        |     case e: Exception => throw e
        |   }
        |   finally res.close()
        | }
        | val res = new java.io.FileWriter(dscAvgCsvFile, ${Overlap.append})
        | try {
        |   dscAvg.foreach {
        |     case (parId, v) =>
        |       if (${Overlap.append}) res.write(s"$$regId,$$parId,")
        |       res.write(v.mkString(",") + "\\n")
        |   }
        | }
        | catch {
        |   case e: Exception => throw e
        | }
        | finally res.close()
      """.stripMargin) set (
        name    := s"${reg.id}-WriteMeanDsc",
        inputs  += (regId.toArray, parId.toArray, dscGrpAvg.toArray, header),
        header  := Overlap.groups.toArray
      )

    val writeMeanJsi = ScalaTask(
      s"""
        | val regId = input.regId.head
        | val jsiAvgCsvFile = new java.io.File(s"$jsiAvgCsvPath")
        | val jsiAvg = (jsiGrpAvg zip parId).groupBy(_._2).map {
        |   case (id, results) => id -> results.map(_._1).transpose.map(_.sum / results.size.toDouble)
        | }.toArray.sortBy(_._1)
        | if (!jsiAvgCsvFile.exists) {
        |   val res = new java.io.FileWriter(jsiAvgCsvFile)
        |   try {
        |     if (${Overlap.append}) res.write("Registration,Parameters,")
        |     res.write(header.mkString(",") + "\\n")
        |   }
        |   catch {
        |     case e: Exception => throw e
        |   }
        |   finally res.close()
        | }
        | val res = new java.io.FileWriter(jsiAvgCsvFile, ${Overlap.append})
        | try {
        |   jsiAvg.foreach {
        |     case (parId, v) =>
        |       if (${Overlap.append}) res.write(s"$$regId,$$parId,")
        |       res.write(v.mkString(",") + "\\n")
        |   }
        | }
        | catch {
        |   case e: Exception => throw e
        | }
        | finally res.close()
      """.stripMargin) set (
        name    := s"${reg.id}-WriteMeanJsi",
        inputs  += (regId.toArray, parId.toArray, jsiGrpAvg.toArray, header),
        header  := Overlap.groups.toArray
      )

    if (dscEnabled && jsiEnabled)
      (forEachPar -< Capsule(incParId, strainer = true) -- backupResults >- backupEnded) +
        (backupEnded -- forEachPar -< Capsule(incParId, strainer = true) -- forEachImPair -< (evalOverlap on Env.short)) +
        (evalOverlap -- (writeJsiVal, writeJsiAvg, writeJsiStd)) + (evalOverlap >- writeMeanJsi) +
        (evalOverlap -- (writeDscVal, writeDscAvg, writeDscStd)) + (evalOverlap >- writeMeanDsc)
    else if (dscEnabled)
      (forEachPar -< Capsule(incParId, strainer = true) -- backupResults >- backupEnded) +
        (backupEnded -- forEachPar -< Capsule(incParId, strainer = true) -- forEachImPair -< (evalOverlap on Env.short)) +
        (evalOverlap -- (writeDscVal, writeDscAvg, writeDscStd)) + (evalOverlap >- writeMeanDsc)
    else if (jsiEnabled)
      (forEachPar -< Capsule(incParId, strainer = true) -- backupResults >- backupEnded) +
        (backupEnded -- forEachPar -< Capsule(incParId, strainer = true) -- forEachImPair -< (evalOverlap on Env.short)) +
        (evalOverlap -- (writeJsiVal, writeJsiAvg, writeJsiStd)) + (evalOverlap >- writeMeanJsi)
    else
      EmptyTask().toCapsule.toPuzzle
  }
}
