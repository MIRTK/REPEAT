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
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Assess segmentation overlap after registration
 */
object EvaluateOverlap {

  /**
   *
   * @param regRegistration whose label overlap should be evaluated
   */
  def apply(reg: Registration) = {

    // -----------------------------------------------------------------------------------------------------------------
    // Configuration
    import Dataset.{imgCsv, segPre, segSuf}

    // -----------------------------------------------------------------------------------------------------------------
    // Constants
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

    val labels = Overlap.labels.mkString(",")
    val groups = Overlap.groups.mkString(",")

    val segPath = FileUtil.relativize(Workspace.dir, reg.segDir)

    // -----------------------------------------------------------------------------------------------------------------
    // Variables
    val regId  = Val[String]               // Registration ID/name
    val parIdx = Val[Int]                  // Parameter set ID ("params" row index)
    val parId  = Val[String]               // Parameter set ID as fixed width string with leading zeros
    val parVal = Val[Map[String, String]]  // Map from parameter name to value
    val tgtId  = Val[Int]                  // ID of target image
    val tgtSeg = Val[File]                 // Target segmentation image
    val srcId  = Val[Int]                  // ID of source image
    val outSeg = Val[File]                 // Deformed source segmentation

    val dscVal    = Val[Array[Double]]     // Dice similarity coefficient (DSC) for each label and segmentation
    val dscGrpAvg = Val[Array[Double]]     // Mean DSC for each label group and segmentation
    val dscGrpStd = Val[Array[Double]]     // Standard deviation of DSC for each label group and segmentation

    val dscValCsv    = Val[List[String]]
    val dscGrpAvgCsv = Val[List[String]]
    val dscGrpStdCsv = Val[List[String]]

    val dscValSkip    = Val[Boolean]
    val dscGrpAvgSkip = Val[Boolean]
    val dscGrpStdSkip = Val[Boolean]

    val jsiVal    = Val[Array[Double]]     // Jaccard similarity index (JSI) for each label and segmentation
    val jsiGrpAvg = Val[Array[Double]]     // Mean JSI for each label group and segmentation
    val jsiGrpStd = Val[Array[Double]]     // Standard deviation of JSI for each label group and segmentation

    val jsiValCsv    = Val[List[String]]
    val jsiGrpAvgCsv = Val[List[String]]
    val jsiGrpStdCsv = Val[List[String]]

    val jsiValSkip    = Val[Boolean]
    val jsiGrpAvgSkip = Val[Boolean]
    val jsiGrpStdSkip = Val[Boolean]

    // -----------------------------------------------------------------------------------------------------------------
    // Samplings
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal) zipWithIndex parIdx
    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")

    // -----------------------------------------------------------------------------------------------------------------
    // Tasks
    val forEachPar =
      ExplorationTask(paramSampling) set (
        name    := s"${reg.id}-ForEachPar",
        outputs += regId,
        regId   := reg.id
      )

    val setParId = SetParId(reg, paramSampling, parIdx, parId)

    val forEachImPair =
      Capsule(
        ExplorationTask(
          imageSampling x
            (tgtSeg in SelectFileDomain(Workspace.segDir, segPre + "${tgtId}" + segSuf)) x
            (outSeg in SelectFileDomain(Workspace.dir, segPath + File.separator + segPre + "${srcId}-${tgtId}" + segSuf))
        ) set (
          name    := s"${reg.id}-ForEachImPair",
          inputs  += (regId, parId),
          outputs += (regId, parId),
          regId   := reg.id
        ),
        strainer = true
      )

    // Read overlap results from CSV files written by previous evaluation
    def readCsv(csvPath: String, lines: Prototype[List[String]], enabled: Boolean) =
      Capsule(
        ScalaTask(
          s"""
            | val ${lines.name} =
            |   if ($enabled) {
            |     try fromFile(s"$csvPath").getLines().toList
            |     catch {
            |       case _: FileNotFoundException => List[String]()
            |       case e: Exception => throw e
            |     }
            |   }
            |   else List[String]()
          """.stripMargin
        ) set (
          name    := s"${reg.id}-${if (enabled) "Read" else "Ignore"}${lines.name.capitalize}",
          imports += ("scala.io.Source.fromFile", "java.io.FileNotFoundException"),
          inputs  += (regId, parId),
          outputs += lines
        ),
        strainer = true
      )

    // Get previous overlap result from specific line of input/output CSV
    def getValues(lines: Prototype[List[String]], values: Prototype[Array[Double]], skip: Prototype[Boolean], enabled: Boolean) =
      Capsule(
        ScalaTask(
          s"""
            | val ${values.name} = ${lines.name}.view.filter(_.startsWith(s"$$tgtId,$$srcId,")).headOption match {
            |   case Some(line) => line.split(",").drop(2).map(_.toDouble)
            |   case None => Array[Double]()
            | }
            | val ${skip.name} = ${values.name}.size > 0
          """.stripMargin
        ) set (
          name    := s"${reg.id}-${if (enabled) "Get" else "Ignore"}${values.name.capitalize}",
          inputs  += (regId, parId, tgtId, srcId, lines),
          outputs += (values, skip)
        ),
        strainer = true
      )

    // Evaluate overlap of given registration result
    // Note: Evaluate label statistics (confusion matrix entries) only once and calculate the different
    //       requested overlap measures from these numbers to save redundant label comparisons
    val evalTask =
      Capsule(
        ScalaTask(
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
          """.stripMargin
        ) set (
          name        := s"${reg.id}-EvalOverlap",
          imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Overlap}",
          usedClasses += (Config.getClass, IRTK.getClass, Overlap.getClass),
          inputs      += (regId, parId, tgtId, srcId),
          inputFiles  += (tgtSeg, "target" + segSuf, link = Workspace.shared),
          inputFiles  += (outSeg, "labels" + segSuf, link = Workspace.shared),
          outputs     += (regId, parId, tgtId, srcId, dscVal, dscGrpAvg, dscGrpStd, jsiVal, jsiGrpAvg, jsiGrpStd)
        ),
        strainer = true
      )

    // Capsule which indicates end of overlap evaluation task
    val evalEnd =
      Capsule(
        EmptyTask() set (
          name    := s"${reg.id}-EvalOverlapEnd",
          inputs  += (dscVal, dscGrpAvg, dscGrpStd, jsiVal, jsiGrpAvg, jsiGrpStd),
          outputs += (dscVal, dscGrpAvg, dscGrpStd, jsiVal, jsiGrpAvg, jsiGrpStd)
        ),
        strainer = true
      )

    // Write overlap of individual registration result
    def writeValues(csvPath: String, values: Prototype[Array[Double]], skip: Prototype[Boolean], header: String, enabled: Boolean) =
      ScalaTask(
        s"""
          | if ($enabled && !${skip.name}) {
          |   val csv = new java.io.File(s"$csvPath")
          |   val hdr = if (csv.exists) "" else "Target,Source,$header\\n"
          |   csv.getParentFile.mkdirs()
          |   val fw = new java.io.FileWriter(csv, true)
          |   try fw.write(hdr + tgtId + "," + srcId + "," + ${values.name}.mkString(",") + "\\n")
          |   finally fw.close()
          | }
        """.stripMargin
      ) set (
        name   := s"${reg.id}-${if (enabled)"Write" else "Ignore"}${values.name.capitalize}",
        inputs += (regId, parId, tgtId, srcId, values, skip)
      )

    // Write mean values calculated over all registration results computed with a fixed set of parameters
    def writeMeanValues(csvPath: String, avg: Prototype[Array[Double]], header: String, enabled: Boolean) =
      ScalaTask(
        s"""
          | val regId  = input.regId.head
          | val values = (${avg.name} zip parId).groupBy(_._2).map {
          |   case (id, results) => id -> results.map(_._1).transpose.map(_.sum / results.size)
          | }.toArray.sortBy(_._1)
          | val csv = new java.io.File(s"$csvPath")
          | val hdr = !csv.exists
          | val fw  = new java.io.FileWriter(csv, ${Overlap.append})
          | try {
          |   if (hdr) {
          |     if (${Overlap.append}) fw.write("Registration,Parameters,")
          |     fw.write("$header\\n")
          |   }
          |   values.foreach {
          |     case (parId, v) =>
          |       if (${Overlap.append}) fw.write(s"$$regId,$$parId,")
          |       fw.write(v.mkString(",") + "\\n")
          |   }
          | }
          | catch {
          |   case e: Exception => throw e
          | }
          | finally fw.close()
        """.stripMargin
      ) set (
        name   := s"${reg.id}-Write${avg.name.capitalize}",
        inputs += (regId.toArray, parId.toArray, avg.toArray)
      )

    // -----------------------------------------------------------------------------------------------------------------
    // Workflow
    val readPrevResults =
      readCsv(dscValCsvPath,    dscValCsv,    enabled = dscEnabled) --
      readCsv(dscGrpAvgCsvPath, dscGrpAvgCsv, enabled = dscEnabled) --
      readCsv(dscGrpStdCsvPath, dscGrpStdCsv, enabled = dscEnabled) --
      readCsv(jsiValCsvPath,    jsiValCsv,    enabled = jsiEnabled) --
      readCsv(jsiGrpAvgCsvPath, jsiGrpAvgCsv, enabled = jsiEnabled) --
      readCsv(jsiGrpStdCsvPath, jsiGrpStdCsv, enabled = jsiEnabled)

    val evalBegin =
      getValues(dscValCsv,    dscVal,    dscValSkip,    enabled = dscEnabled) --
      getValues(dscGrpAvgCsv, dscGrpAvg, dscGrpAvgSkip, enabled = dscEnabled) --
      getValues(dscGrpStdCsv, dscGrpStd, dscGrpStdSkip, enabled = dscEnabled) --
      getValues(jsiValCsv,    jsiVal,    jsiValSkip,    enabled = jsiEnabled) --
      getValues(jsiGrpAvgCsv, jsiGrpAvg, jsiGrpAvgSkip, enabled = jsiEnabled) --
      getValues(jsiGrpStdCsv, jsiGrpStd, jsiGrpStdSkip, enabled = jsiEnabled)

    val eval =
      forEachPar -< setParId -- readPrevResults -- forEachImPair -< evalBegin --
        Skip(
          evalTask on Env.short,
          "dscValSkip && dscGrpAvgSkip && dscGrpStdSkip && jsiValSkip && jsiGrpAvgSkip && jsiGrpStdSkip"
        ) --
      evalEnd

    val write =
      evalEnd -- (
        writeValues(dscValCsvPath,    dscVal,    dscValSkip,    header = labels, enabled = dscEnabled),
        writeValues(dscGrpAvgCsvPath, dscGrpAvg, dscGrpAvgSkip, header = groups, enabled = dscEnabled),
        writeValues(dscGrpStdCsvPath, dscGrpStd, dscGrpStdSkip, header = groups, enabled = dscEnabled),
        writeValues(jsiValCsvPath,    jsiVal,    jsiValSkip,    header = labels, enabled = jsiEnabled),
        writeValues(jsiGrpAvgCsvPath, jsiGrpAvg, jsiGrpAvgSkip, header = groups, enabled = jsiEnabled),
        writeValues(jsiGrpStdCsvPath, jsiGrpStd, jsiGrpStdSkip, header = groups, enabled = jsiEnabled)
      )

    val writeMean =
      evalEnd >- (
        writeMeanValues(dscAvgCsvPath, dscGrpAvg, header = groups, enabled = dscEnabled),
        writeMeanValues(jsiAvgCsvPath, jsiGrpAvg, header = groups, enabled = jsiEnabled)
      )

    eval + write + writeMean
  }
}
