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

import java.nio.file.{Path, Paths}

import scala.language.reflectiveCalls

import com.andreasschuh.repeat.core.{Environment => Env, _}
import com.andreasschuh.repeat.puzzle._

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.{Skip, Switch, Case}


/**
 * Run registration with different parameters and store results for evaluation
 */
object Evaluate {

  /**
   * Construct workflow puzzle
   *
   * @param reg Registration info
   *
   * @return Workflow puzzle for running the registration and generating the results needed for quality assessment
   */
  def apply(reg: Registration) = {

    // -----------------------------------------------------------------------------------------------------------------
    // Constants
    import Dataset.{imgDir => _, segDir => _, _}
    import Workspace.{imgDir, segDir, dofAff, dofPre, dofSuf, logDir, logSuf}

    val labels = Overlap.labels.mkString(",")
    val groups = Overlap.groups.mkString(",")

    // Input/intermediate files
    val tgtImPath  = Paths.get(    imgDir.getAbsolutePath, imgPre + "${tgtId}"          +     imgSuf).toString
    val srcImPath  = Paths.get(    imgDir.getAbsolutePath, imgPre + "${srcId}"          +     imgSuf).toString
    val tgtSegPath = Paths.get(    segDir.getAbsolutePath, segPre + "${tgtId}"          +     segSuf).toString
    val srcSegPath = Paths.get(    segDir.getAbsolutePath, segPre + "${srcId}"          +     segSuf).toString
    val iniDofPath = Paths.get(    dofAff.getAbsolutePath, dofPre + "${tgtId},${srcId}" +     dofSuf).toString
    val affDofPath = Paths.get(reg.affDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.affSuf).toString
    val outDofPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" +     dofSuf).toString
    val outJacPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.jacSuf).toString
    val outImPath  = Paths.get(reg.imgDir.getAbsolutePath, imgPre + "${srcId}-${tgtId}" +     imgSuf).toString
    val outSegPath = Paths.get(reg.segDir.getAbsolutePath, segPre + "${srcId}-${tgtId}" +     segSuf).toString
    val outLogPath = Paths.get(    logDir.getAbsolutePath, "${regId}-${parId}", "${tgtId},${srcId}" + logSuf).toString

    // Evaluation result files
    val runTimeCsvPath = Paths.get(reg.resDir.getAbsolutePath, "Time.csv").toString
    val avgTimeCsvPath = Paths.get(reg.sumDir.getAbsolutePath, "Time.csv").toString

    val outSimCsvPath = Paths.get(reg.resDir.getAbsolutePath, "Similarity.csv").toString

    val dscRegAvgCsvName = Overlap.summary.replace("${measure}", "DSC")
    val dscValuesCsvPath = Paths.get(reg.resDir.getAbsolutePath, "DSC_Label.csv" ).toString
    val dscGrpAvgCsvPath = Paths.get(reg.resDir.getAbsolutePath, "DSC_Mean.csv"  ).toString
    val dscGrpStdCsvPath = Paths.get(reg.resDir.getAbsolutePath, "DSC_Sigma.csv" ).toString
    val dscRegAvgCsvPath = Paths.get(reg.sumDir.getAbsolutePath, dscRegAvgCsvName).toString

    val jsiRegAvgCsvName = Overlap.summary.replace("${measure}", "JSI")
    val jsiValuesCsvPath = Paths.get(reg.resDir.getAbsolutePath, "JSI_Label.csv" ).toString
    val jsiGrpAvgCsvPath = Paths.get(reg.resDir.getAbsolutePath, "JSI_Mean.csv"  ).toString
    val jsiGrpStdCsvPath = Paths.get(reg.resDir.getAbsolutePath, "JSI_Sigma.csv" ).toString
    val jsiRegAvgCsvPath = Paths.get(reg.sumDir.getAbsolutePath, jsiRegAvgCsvName).toString

    // Which intermediate result files to keep
    val keepOutDof = true
    val keepOutIm  = true
    val keepOutSeg = true
    val keepOutJac = true

    // Which evaluation measures to compute
    val timeEnabled = true
    val simEnabled  = Array("SSD", "CC", "MI", "NMI")
    val dscEnabled  = Overlap.measures contains Overlap.DSC
    val jsiEnabled  = Overlap.measures contains Overlap.JSI

    val regSet = "{regId=${regId}, parId=${parId}, tgtId=${tgtId}, srcId=${srcId}}"
    val avgSet = "{regId=${regId}, parId=${parId}}"

    // -----------------------------------------------------------------------------------------------------------------
    // Variables

    // Input/output variables
    val regId   = Val[String]              // ID of registration
    val parIdx  = Val[Int]                 // Row index of parameter set
    val parId   = Val[String]              // ID of parameter set
    val parVal  = Val[Map[String, String]] // Map from parameter name to value
    val tgtId   = Val[Int]                 // ID of target image
    val tgtIm   = Val[Path]                // Fixed target image
    val tgtSeg  = Val[Path]                // Segmentation of target image
    val srcId   = Val[Int]                 // ID of source image
    val srcIm   = Val[Path]                // Moving source image
    val srcSeg  = Val[Path]                // Segmentation of source image
    val iniDof  = Val[Path]                // Pre-computed affine transformation from target to source
    val affDof  = Val[Path]                // Affine transformation converted to input format
    val outDof  = Val[Path]                // Output transformation converted to IRTK format
    val outIm   = Val[Path]                // Deformed source image
    val outSeg  = Val[Path]                // Deformed source segmentation
    val outJac  = Val[Path]                // Jacobian determinant map
    val outLog  = Val[Path]                // Registration command output log file
    val simHdr  = Val[Array[String]]       // Names of similarity metrics to compute
    val outSim  = Val[Map[String, Double]] // Intensity similarity of output image compared to target image

    val outImModified  = Val[Boolean]      // Whether deformed source image was newly created
    val outSegModified = Val[Boolean]      // Whether deformed source segmentation was newly created

    // Evaluation results
    val runTime = Val[Array[Double]]       // Runtime of registration command
    val avgTime = Val[Array[Double]]       // Mean runtime over all registrations for a given set of parameters

    val runTimeValid = Val[Boolean]        // Whether runtime measurements read from previous results CSV are valid
    val avgTimeValid = Val[Boolean]        // ...

    val dscValues = Val[Array[Double]]     // Dice similarity coefficient (DSC) for each label and segmentation
    val dscGrpAvg = Val[Array[Double]]     // Mean DSC for each label group and segmentation
    val dscGrpStd = Val[Array[Double]]     // Standard deviation of DSC for each label group and segmentation
    val dscRegAvg = Val[Array[Double]]     // Average mean DSC for a given set of registration parameters

    val dscValuesValid = Val[Boolean]      // Whether DSC values read from previous CSV are valid
    val dscGrpAvgValid = Val[Boolean]      // ...
    val dscGrpStdValid = Val[Boolean]      // ...
    val dscRegAvgValid = Val[Boolean]      // ...

    val jsiValues = Val[Array[Double]]     // Jaccard similarity index (JSI) for each label and segmentation
    val jsiGrpAvg = Val[Array[Double]]     // Mean JSI for each label group and segmentation
    val jsiGrpStd = Val[Array[Double]]     // Standard deviation of JSI for each label group and segmentation
    val jsiRegAvg = Val[Array[Double]]     // Average mean JSI for a given set of registration parameters

    val jsiValuesValid = Val[Boolean]      // Whether JSI values read from previous CSV are valid
    val jsiGrpAvgValid = Val[Boolean]      // ...
    val jsiGrpStdValid = Val[Boolean]      // ...
    val jsiRegAvgValid = Val[Boolean]      // ...

    // -----------------------------------------------------------------------------------------------------------------
    // Samplings
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal)
    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")

    // -----------------------------------------------------------------------------------------------------------------
    // Auxiliaries

    // NOP
    def nopCap(taskName: String) =
      Capsule(
        EmptyTask() set (
          name := s"${reg.id}-$taskName-NOP"
        )
      )
    def nop(taskName: String) = nopCap(taskName).toPuzzle

    // Exploration tasks
    def putRegId =
      EmptyTask() set (
        name    := s"${reg.id}-SetRegId",
        outputs += regId,
        regId   := reg.id
      )

    def forEachPar =
      ExplorationTask(paramSampling zipWithIndex parIdx) set (
        name    := s"${reg.id}-ForEachPar",
        inputs  += regId,
        outputs += regId
      )

    def putParId =
      ScalaTask(
        """
          | val parId  = input.parVal.getOrElse("ID", f"$parIdx%02d")
          | val parVal = input.parVal - "ID"
        """.stripMargin
      ) set (
        name    := s"${reg.id}-SetParId",
        inputs  += (regId, parIdx, parVal),
        outputs += (regId, parId, parVal)
      )

    def demux(taskName: String, input: Prototype[_]*) = {
      val inputNames = input.toSeq.map(_.name)
      val task =
        ScalaTask(inputNames.map(name => s"val $name = input.$name.head").mkString("\n")) set (
          name := s"${reg.id}-$taskName"
        )
      input.foreach(p => {
        task.addInput(p.toArray)
        task.addOutput(p)
      })
      Capsule(task)
    }

    def forEachImPair =
      ExplorationTask(imageSampling) set (
        name    := s"${reg.id}-ForEachImPair",
        inputs  += regId,
        outputs += regId
      )

    def end(taskName: String) =
      Capsule(
        EmptyTask() set (
          name    := s"${reg.id}-$taskName",
          inputs  += regId,
          outputs += regId
        ),
        strainer = true
      )

    // Delete table with summary results which can be recomputed from individual result tables
    def deleteTable(path: String, enabled: Boolean) =
      if (enabled)
        ScalaTask(
          s"""
            | val table = Paths.get(s"$path")
            | if (Files.exists(table)) {
            |   Files.delete(table)
            |   println(s"$${DONE}Delete $${table.getFileName} for {regId=$${regId}}")
            | }
          """.stripMargin
        ) set (
          name    := s"${reg.id}-DeleteTable",
          imports += ("java.nio.file.{Paths, Files}", "com.andreasschuh.repeat.core.Prefix.DONE"),
          inputs  += regId,
          outputs += regId
        )
      else
        EmptyTask() set (
          name    := s"${reg.id}-KeepTable",
          inputs  += regId,
          outputs += regId
        )

    // Make copy of previous result tables and merge them with previously copied results to ensure none are lost
    def backupTablePath(path: String) = {
      val p = Paths.get(path)
      p.getParent.resolve(FileUtil.hidden(p.getFileName.toString)).toString
    }

    def backupTable(path: String, enabled: Boolean) =
      if (enabled)
        ScalaTask(
          s"""
            | val from = new java.io.File(s"$path")
            | val to   = new java.io.File(s"${backupTablePath(path)}")
            | if (from.exists) {
            |   val l1 = if (to.exists) fromFile(to).getLines().toList.drop(1) else List[String]()
            |   val l2 = fromFile(from).getLines().toList
            |   val fw = new java.io.FileWriter(to)
            |   try {
            |     fw.write(l2.head + "\\n")
            |     val l: List[String] = (l1 ::: l2.tail).groupBy( _.split(",").take(2).mkString(",") ).map(_._2.last)(breakOut)
            |     l.sortBy( _.split(",").take(2).mkString(",") ).foreach( row => fw.write(row + "\\n") )
            |   }
            |   finally fw.close()
            |   java.nio.file.Files.delete(from.toPath)
            |   println(s"$${DONE}Backup $${from.getName} for $avgSet")
            | }
          """.stripMargin
        ) set (
          name    := s"${reg.id}-BackupTable",
          imports += ("com.andreasschuh.repeat.core.Prefix.DONE", "scala.io.Source.fromFile", "scala.collection.breakOut"),
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
      else
        EmptyTask() set (
          name    := s"${reg.id}-KeepTable",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )

    // Finalize result table, appending non-overwritten previous results again and sorting the final table
    def finalizeTable(path: String, enabled: Boolean) =
      if (enabled)
        ScalaTask(
          s"""
            | val from = new java.io.File(s"${backupTablePath(path)}")
            | val to   = new java.io.File(s"$path")
            | if (from.exists) {
            |   val l1 = fromFile(from).getLines().toList
            |   val l2 = if (to.exists) fromFile(to).getLines().toList.tail else List[String]()
            |   val fw = new java.io.FileWriter(to)
            |   try {
            |     fw.write(l1.head + "\\n")
            |     val l: List[String] = (l1.tail ::: l2).groupBy( _.split(",").take(2).mkString(",") ).map(_._2.last)(breakOut)
            |     l.sortBy( _.split(",").take(2).mkString(",") ).foreach( row => fw.write(row + "\\n") )
            |   }
            |   finally fw.close()
            |   java.nio.file.Files.delete(from.toPath)
            |   println(s"$${DONE}Finalize $${to.getName} for $avgSet")
            | }
          """.stripMargin
        ) set (
          name    := s"${reg.id}-FinalizeTable",
          imports += ("scala.io.Source.fromFile", "scala.collection.breakOut", "com.andreasschuh.repeat.core.Prefix.DONE"),
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
      else
        EmptyTask() set (
          name    := s"${reg.id}-KeepTable",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )

    // Initial conversion of affine input transformation
    val convertDofinBegin =
      Capsule(
        ScalaTask(
          s"""
            | val iniDof = Paths.get(s"$iniDofPath")
          """.stripMargin
        ) set (
          name    := s"${reg.id}-ConvertDofToAffBegin",
          imports += "java.nio.file.Paths",
          inputs  += (regId, tgtId, srcId),
          outputs += (regId, tgtId, srcId, iniDof)
        )
      )

    // Auxiliary tasks for main registration task
    val registerImagesBegin =
      Capsule(
        ScalaTask(
          s"""
            | val tgtIm  = Paths.get(s"$tgtImPath")
            | val srcIm  = Paths.get(s"$srcImPath")
            | val affDof = Paths.get(s"$affDofPath")
            | val outDof = Paths.get(s"$outDofPath")
            | val outLog = Paths.get(s"$outLogPath")
          """.stripMargin
        ) set (
          name    := s"${reg.id}-RegisterImagesBegin",
          imports += "java.nio.file.Paths",
          inputs  += (regId, parId, tgtId, srcId),
          outputs += (regId, parId, tgtId, srcId, tgtIm, srcIm, affDof, outDof, outLog)
        ),
        strainer = true
      )

    // Read previous result from backup table to save recomputation if nothing changed
    def readFromTable(path: String, p: Prototype[Array[Double]], isValid: Prototype[Boolean],
                      n: Int = 0, invalid: Double = Double.NaN, enabled: Boolean = true) =
      Capsule(
        ScalaTask(
          s"""
            | val ${p.name} =
            |   if ($enabled)
            |     try {
            |       val file  = new java.io.File(s"${backupTablePath(path)}")
            |       val lines = fromFile(file).getLines().toList.view
            |       val row   = lines.filter(_.startsWith(s"$$tgtId,$$srcId,")).last.split(",")
            |       val ncols = ${n + 2}
            |       if (ncols > 2 && row.size != ncols) throw new Exception(s"Expected $$ncols columns in CSV table $${file.getPath}")
            |       row.drop(2).map(_.toDouble)
            |     }
            |     catch {
            |       case _: Exception => Array.fill[Double]($n)($invalid)
            |     }
            |   else Array[Double]()
            |
            | val ${isValid.name} = !${p.name}.isEmpty && !${p.name}.contains($invalid)
            | if (${isValid.name}) println(s"$${HAVE}${p.name.capitalize} for $regSet")
          """.stripMargin
        ) set (
          name    := s"${reg.id}-Read${p.name.capitalize}",
          imports += ("scala.io.Source.fromFile", "Double.NaN", "com.andreasschuh.repeat.core.Prefix.HAVE"),
          inputs  += (regId, parId, tgtId, srcId),
          outputs += (regId, parId, tgtId, srcId, p, isValid)
        ),
        strainer = true
      )

    def readMapFromTable(path: String, map: Prototype[Map[String, Double]], columns: Prototype[Array[String]], enabled: Array[String]) =
      Capsule(
        ScalaTask(
          s"""
            | val ${map.name} =
            |   if (${columns.name}.nonEmpty)
            |     try {
            |       val file   = new java.io.File(s"${backupTablePath(path)}")
            |       val lines  = fromFile(file).getLines().toList
            |       val header = lines.head.split(",").zipWithIndex.toMap.filterKeys(${columns.name}.contains)
            |       val values = lines.view.filter(_.startsWith(s"$$tgtId,$$srcId,")).last.split(",")
            |       header.mapValues(values(_).toDouble)
            |     }
            |     catch {
            |       case _: Exception => Map[String, Double]()
            |     }
            |   else Map[String, Double]()
            |
            | ${map.name}.foreach { case (name, _) => println(s"$${HAVE}$${name.capitalize} for $regSet") }
          """.stripMargin
        ) set (
          name    := s"${reg.id}-Read${map.name.capitalize}",
          imports += ("scala.io.Source.fromFile", "com.andreasschuh.repeat.core.Prefix.HAVE"),
          inputs  += (regId, parId, tgtId, srcId, columns),
          outputs += (regId, parId, tgtId, srcId, columns, map),
          columns := enabled
        ),
        strainer = true
      )

    def saveMapToTable(path: String, map: Prototype[Map[String, Double]], columns: Prototype[Array[String]], enabled: Array[String]) =
      if (enabled.isEmpty) nopCap("SaveMapToTable").toPuzzlePiece else {
        val measures = Val[Array[Double]]
        Capsule(
          ScalaTask(
            s"""
               | val measures = ${columns.name}.map(${map.name}(_))
               | println(s"$${SAVE}${map.name.capitalize} for $regSet")
             """.stripMargin) set (
            name    := s"${reg.id}-Save${map.name.capitalize}",
            imports += "com.andreasschuh.repeat.core.Prefix.SAVE",
            inputs  += (regId, parId, tgtId, srcId, map, columns),
            outputs += (regId, parId, tgtId, srcId, measures),
            columns := enabled
          )
        ) hook (
          AppendToCSVFileHook(path, tgtId, srcId, measures) set (
            csvHeader := "Target,Source," + enabled.mkString(","),
            singleRow := true
          )
        )
      }

    // Write individual registration result to CSV table
    def saveToTable(path: String, result: Prototype[Array[Double]], header: String) =
      ScalaTask(s"""println(s"$${SAVE}${result.name.capitalize} for $regSet") """) set (
        name    := s"${reg.id}-Save${result.name.capitalize}",
        imports += "com.andreasschuh.repeat.core.Prefix.SAVE",
        inputs  += (regId, parId, tgtId, srcId, result),
        outputs += (regId, parId, tgtId, srcId, result)
      ) hook (
        AppendToCSVFileHook(path, tgtId, srcId, result) set (
          csvHeader := "Target,Source," + header,
          singleRow := true
        )
      )

    // Calculate mean of values over all registration results computed with a fixed set of parameters
    def calcMean(result: Prototype[Array[Double]], resultValid: Prototype[Boolean],
                 mean:   Prototype[Array[Double]], meanValid:   Prototype[Boolean]) =
      ScalaTask(
        s"""
          | val regId = input.regId.head
          | val parId = input.parId.head
          | val ${meanValid.name} = ${result.name}.size > 0 && !${resultValid.name}.contains(false)
          | val ${mean.name} =
          |   if (!${meanValid.name}) Double.NaN
          |   else ${result.name}.transpose.map(_.sum / ${result.name}.size)
        """.stripMargin
      ) set (
        name    := s"${reg.id}-Save${mean.name.capitalize}",
        inputs  += (regId.toArray, parId.toArray, result.toArray, resultValid.toArray),
        outputs += (regId, parId, mean, meanValid)
      )

    // Write mean values calculated over all registration results computed with a fixed set of parameters to CSV table
    def saveToSummaryTable(path: String, mean: Prototype[Array[Double]], header: String) =
      ScalaTask(s"""println(s"$${SAVE}${mean.name.capitalize} for $avgSet") """) set (
        name    := s"${reg.id}-Save${mean.name.capitalize}",
        imports += "com.andreasschuh.repeat.core.Prefix.SAVE",
        inputs  += (regId, parId, mean),
        outputs += (regId, parId, mean)
      ) hook (
        AppendToCSVFileHook(path, regId, parId, mean) set (
          csvHeader := "Registration,Parameters," + header,
          singleRow := true
        )
      )

    // -----------------------------------------------------------------------------------------------------------------
    // Backup previous result tables
    val backupTablesEnd = demux("BackupTablesEnd", regId)
    def backupTables =
      putRegId --
      // Delete summary tables with mean values
      deleteTable(avgTimeCsvPath,   timeEnabled && !Workspace.append) --
      deleteTable(dscRegAvgCsvPath, dscEnabled  && !Workspace.append) --
      deleteTable(jsiRegAvgCsvPath, jsiEnabled  && !Workspace.append) --
      // Results of individual registrations
      forEachPar -< putParId --
        backupTable(runTimeCsvPath,   timeEnabled) --
        backupTable(dscValuesCsvPath, dscEnabled) --
        backupTable(dscGrpAvgCsvPath, dscEnabled) --
        backupTable(dscGrpStdCsvPath, dscEnabled) --
        backupTable(jsiValuesCsvPath, jsiEnabled) --
        backupTable(jsiGrpAvgCsvPath, jsiEnabled) --
        backupTable(jsiGrpStdCsvPath, jsiEnabled) >-
      backupTablesEnd

    // -----------------------------------------------------------------------------------------------------------------
    // Prepare input transformation
    val convertDofinEnd = demux("ConvertDofToAffEnd", regId)
    def convertDofin =
      backupTablesEnd -- Capsule(forEachImPair, strainer = true) -<
        convertDofinBegin --
          ConvertDofToAff(reg, regId, tgtId, srcId, iniDof, affDof, affDofPath) >-
        convertDofinEnd

    // -----------------------------------------------------------------------------------------------------------------
    // Run registration command
    val registerImagesEnd = end("RegisterImagesEnd")
    def registerImages = {
      // Execute pairwise registration
      val regCond =
        Condition(
          s"""
            | val tgtIm  = input.tgtIm.toFile
            | val srcIm  = input.srcIm.toFile
            | val iniDof = new java.io.File(s"$iniDofPath")
            | val outDof = new java.io.File(s"$outDofPath")
            | val outIm  = new java.io.File(s"$outImPath")
            | val outSeg = new java.io.File(s"$outSegPath")
            | val outJac = new java.io.File(s"$outJacPath")
            |
            | def updateOutDof = $keepOutDof &&
            |   outDof.lastModified < iniDof.lastModified &&
            |   outDof.lastModified < tgtIm .lastModified &&
            |   outDof.lastModified < srcIm .lastModified
            | def updateOutIm = $keepOutIm &&
            |   outIm.lastModified < iniDof.lastModified &&
            |   outIm.lastModified < tgtIm .lastModified &&
            |   outIm.lastModified < srcIm .lastModified
            | def updateOutSeg = $keepOutSeg &&
            |   outSeg.lastModified < iniDof.lastModified &&
            |   outSeg.lastModified < tgtIm .lastModified &&
            |   outSeg.lastModified < srcIm .lastModified
            | def updateOutJac = $keepOutJac &&
            |   outJac.lastModified < iniDof.lastModified &&
            |   outJac.lastModified < tgtIm .lastModified &&
            |   outJac.lastModified < srcIm .lastModified
            |
            | updateOutDof || (!outDof.exists && (updateOutIm || updateOutSeg || updateOutJac)) || ($timeEnabled && !runTimeValid)
          """.stripMargin
        )
      def regImPair =
        RegisterImages(reg, regId, parId, parVal, tgtId, srcId, tgtIm, srcIm, affDof, outDof, outLog, runTime, runTimeValid)
      def runReg =
        convertDofinEnd -- forEachPar -< putParId --
          Capsule(forEachImPair, strainer = true) -<
            registerImagesBegin --
              readFromTable(runTimeCsvPath, runTime, runTimeValid, n = 4, invalid = .0, enabled = timeEnabled) --
              Switch(
                Case( regCond, Display.QSUB(s"Registration for $regSet") -- (regImPair on reg.runEnv) -- Display.DONE(s"Registration for $regSet")),
                Case(!regCond, Display.SKIP(s"Registration for $regSet"))
              ) --
            registerImagesEnd
      // Write runtime measurements
      def saveTime =
        registerImagesEnd -- Switch(
          Case(  "runTimeValid", saveToTable(runTimeCsvPath, runTime, header = "User,System,Total,Real")),
          Case(s"!runTimeValid && $timeEnabled", Display.WARN(s"Missing ${runTime.name.capitalize} for $regSet"))
        ) >- demux("SaveTimeDemux", regId, parId) -- finalizeTable(runTimeCsvPath, timeEnabled)
      // Write mean of runtime measurements
      def saveMeanTime =
        registerImagesEnd >-
          calcMean(runTime, runTimeValid, avgTime, avgTimeValid) -- Switch(
            Case(  "avgTimeValid", saveToSummaryTable(avgTimeCsvPath, avgTime, header = "User,System,Total,Real")),
            Case(s"!avgTimeValid && $timeEnabled", Display.WARN(s"Invalid ${avgTime.name.capitalize} for $avgSet"))
          )
      // Assemble sub-workflow
      runReg + saveTime + saveMeanTime
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Deform source image and quantify residual differences between fixed target and deformed source image
    def evaluateSimilarity = if (!keepOutIm && simEnabled.isEmpty) nop("EvaluateSimilarity") else {
      // Initialize file paths and read previously computed results from backup table
      val outImChg = Val[Boolean]
      def init =
        registerImagesEnd --
        Capsule(
          ScalaTask(
            s"""
              | val tgtIm = Paths.get(s"$tgtImPath")
              | val srcIm = Paths.get(s"$srcImPath")
              | val outIm = Paths.get(s"$outImPath")
            """.stripMargin
          ) set (
            name     := s"${reg.id}-EvaluateSimilarityInit",
            imports  += "java.nio.file.Paths",
            inputs   += (regId, parId, tgtId, srcId, outDof),
            outputs  += (regId, parId, tgtId, srcId, outDof, tgtIm, srcIm, outIm, outImChg),
            outImChg := false
          )
        ) --
        readMapFromTable(outSimCsvPath, outSim, simHdr, simEnabled)
      val noPrevRes = Condition("simHdr.foldLeft(false)( (b, name) => b || !(outSim contains name) )")
      // Transform and resample source image
      val warpSrcImEnd = end("WarpSrcImEnd")
      def warpSrcIm = {
        val what = "Warp source image for " + regSet
        val template = Val[Cmd]
        val cond = {
          val update =
            Condition(
              s"""
                | outIm.toFile.lastModified < tgtIm.toFile.lastModified ||
                | outIm.toFile.lastModified < srcIm.toFile.lastModified ||
                | ($keepOutDof && outIm.toFile.lastModified < outDof.toFile.lastModified)
              """.stripMargin
            )
          if (keepOutIm) update else noPrevRes && update
        }
        val task =
          Capsule(
            ScalaTask(
              """
                | val args = Map(
                |   "target" -> tgtIm .toString,
                |   "source" -> srcIm .toString,
                |   "out"    -> outIm .toString,
                |   "phi"    -> outDof.toString
                | )
                | val cmd = command(template, args)
                | val outDir = outIm.getParent
                | if (outDir != null) Files.createDirectories(outDir)
                | val ret = cmd.!
                | if (ret != 0) {
                |   val str = cmd.mkString("\"", "\" \"", "\"\n")
                |   throw new Exception("Image deformation command returned non-zero exit code: " + str)
                | }
              """.stripMargin
            ) set (
              name        := s"${reg.id}-WarpSrcIm",
              imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._", "java.nio.file.Files"),
              usedClasses += Registration.getClass,
              inputs      += (regId, parId, tgtId, srcId, tgtIm, srcIm, outIm, outDof, template),
              outputs     += (regId, parId, tgtId, srcId, tgtIm, outIm),
              template    := reg.deformImageCmd,
              outImChg    := true
            ),
            strainer = true
          )
        Switch(
          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
          Case(!cond, Display.SKIP(what))
        ) -- warpSrcImEnd
      }
      // Evaluate residual difference between target and output image
      val evalSimEnd = end("EvalSimEnd")
      def evalSim = if (simEnabled.isEmpty) nop("EvalSim") else {
        val what = "Similarity evaluation for " + regSet
        val cond = noPrevRes || "outImChg"
        val task =
          Capsule(
            ScalaTask(
              s"""
                | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
                | val outSim = IRTK.similarity(tgtIm.toFile, outIm.toFile).filterKeys(simHdr.contains)
              """.stripMargin
            ) set (
              name        := s"${reg.id}-EvalSim",
              imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Prefix}",
              usedClasses += (Config.getClass, IRTK.getClass, Prefix.getClass),
              inputs      += (regId, parId, tgtId, srcId, tgtIm, outIm, simHdr),
              outputs     += (regId, parId, tgtId, srcId, outSim, simHdr)
            )
          )
        warpSrcImEnd -- Switch(
          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
          Case(!cond, Display.SKIP(what))
        ) -- evalSimEnd
      }
      // Save image similarity measures to CSV table
      def saveSim =
        evalSimEnd -- saveMapToTable(outSimCsvPath, outSim, simHdr, simEnabled) >-
          demux("SaveSimDemux", regId, parId) -- finalizeTable(outSimCsvPath, simEnabled.nonEmpty)
      // TODO: Create PNG snapshot of side-by-side view of target and output image
      def snapComp = nop("SnapComp")
      // TODO: Create PNG snapshot of difference image
      def snapDiff = nop("SnapDiff")
      // Assemble sub-workflow
      (init -- warpSrcIm) + evalSim + saveSim + snapComp + snapDiff
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Deform source segmentation and quantify overlap with target labels
    def evaluateOverlap = if (!keepOutSeg && !dscEnabled && !jsiEnabled) nop("EvaluateOverlap") else {
      // Initialize file paths and read previously computed results from backup tables
      val outSegChg = Val[Boolean]
      def init =
        registerImagesEnd --
        Capsule(
          ScalaTask(
            s"""
              | val tgtSeg = Paths.get(s"$tgtSegPath")
              | val srcSeg = Paths.get(s"$srcSegPath")
              | val outSeg = Paths.get(s"$outSegPath")
            """.stripMargin
          ) set (
            name      := s"${reg.id}-EvaluateOverlapInit",
            imports   += "java.nio.file.Paths",
            inputs    += (regId, parId, tgtId, srcId, outDof),
            outputs   += (regId, parId, tgtId, srcId, outDof, tgtSeg, srcSeg, outSeg, outSegChg),
            outSegChg := false
          )
        ) --
        readFromTable(dscValuesCsvPath, dscValues, dscValuesValid, enabled = dscEnabled) --
        readFromTable(dscGrpAvgCsvPath, dscGrpAvg, dscGrpAvgValid, enabled = dscEnabled) --
        readFromTable(dscGrpStdCsvPath, dscGrpStd, dscGrpStdValid, enabled = dscEnabled) --
        readFromTable(jsiValuesCsvPath, jsiValues, jsiValuesValid, enabled = jsiEnabled) --
        readFromTable(jsiGrpAvgCsvPath, jsiGrpAvg, jsiGrpAvgValid, enabled = jsiEnabled) --
        readFromTable(jsiGrpStdCsvPath, jsiGrpStd, jsiGrpStdValid, enabled = jsiEnabled)
      val noPrevRes =
        Condition(
          s"""
            | ($dscEnabled && (!dscValuesValid || !dscGrpAvgValid || !dscGrpStdValid)) ||
            | ($jsiEnabled && (!jsiValuesValid || !jsiGrpAvgValid || !jsiGrpStdValid))
          """.stripMargin
        )
      // Deform source segmentation
      val warpSrcSegEnd = end("WarpSrcSegEnd")
      def warpSrcSeg = {
        val what = "Propagate source labels for " + regSet
        val template = Val[Cmd]
        val cond = {
          val update =
            Condition(
              s"""
                | outSeg.toFile.lastModified < tgtSeg.toFile.lastModified ||
                | outSeg.toFile.lastModified < srcSeg.toFile.lastModified ||
                | ($keepOutDof && outSeg.toFile.lastModified < outDof.toFile.lastModified)
              """.stripMargin
            )
          if (keepOutSeg) update else noPrevRes && update
        }
        val task =
          Capsule(
            ScalaTask(
              """
                | val args = Map(
                |   "target" -> tgtSeg.toString,
                |   "source" -> srcSeg.toString,
                |   "out"    -> outSeg.toString,
                |   "phi"    -> outDof.toString
                | )
                | val cmd = command(template, args)
                | val outDir = outSeg.getParent
                | if (outDir != null) Files.createDirectories(outDir)
                | val ret = cmd.!
                | if (ret != 0) {
                |   val str = cmd.mkString("\"", "\" \"", "\"\n")
                |   throw new Exception("Label propagation command returned non-zero exit code: " + str)
                | }
              """.stripMargin
            ) set (
              name        := s"${reg.id}-PropagateLabels",
              imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._", "java.nio.file.Files"),
              usedClasses += Registration.getClass,
              inputs      += (regId, parId, tgtId, srcId, tgtSeg, srcSeg, outDof, outSeg, template),
              outputs     += (regId, parId, tgtId, srcId, tgtSeg, outSeg, outSegChg),
              template    := reg.deformLabelsCmd,
              outSegChg   := true
            ),
            strainer = true
          )
        Switch(
          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
          Case(!cond, Display.SKIP(what))
        ) -- warpSrcSegEnd
      }
      // Evaluate overlap measures
      val evalOverlapEnd = end("EvalOverlapEnd")
      def evalOverlap = if (!dscEnabled && !jsiEnabled) nop("EvalOverlap") else {
        val what = s"Overlap evaluation for $regSet"
        val cond = noPrevRes || "outSegChg"
        val task =
          Capsule(
            ScalaTask(
              s"""
                | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
                | val stats = IRTK.labelStats(tgtSeg.toFile, outSeg.toFile, Some(Overlap.labels.toSet))
                |
                | val dscMetric = Overlap(stats, Overlap.DSC)
                | val dscValues = dscMetric.toArray
                | val dscGrpAvg = dscMetric.getMeanValues
                | val dscGrpStd = dscMetric.getSigmaValues
                |
                | val jsiMetric = Overlap(stats, Overlap.JSI)
                | val jsiValues = jsiMetric.toArray
                | val jsiGrpAvg = jsiMetric.getMeanValues
                | val jsiGrpStd = jsiMetric.getSigmaValues
                |
                | val dscValuesValid = $dscEnabled
                | val dscGrpAvgValid = $dscEnabled
                | val dscGrpStdValid = $dscEnabled
                | val jsiValuesValid = $jsiEnabled
                | val jsiGrpAvgValid = $jsiEnabled
                | val jsiGrpStdValid = $jsiEnabled
              """.stripMargin
            ) set (
              name        := s"${reg.id}-EvaluateOverlap",
              imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Overlap}",
              usedClasses += (Config.getClass, IRTK.getClass, Overlap.getClass),
              inputs      += (regId, parId, tgtId, srcId, tgtSeg, outSeg),
              outputs     += (regId, parId, tgtId, srcId, dscValues, dscGrpAvg, dscGrpStd, jsiValues, jsiGrpAvg, jsiGrpStd),
              outputs     += (dscValuesValid, dscGrpAvgValid, dscGrpStdValid, jsiValuesValid, jsiGrpAvgValid, jsiGrpStdValid)
            )
          )
        warpSrcSegEnd -- Switch(
          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
          Case(!cond, Display.SKIP(what))
        ) -- evalOverlapEnd
      }
      // Append overlap measures to CSV tables
      def saveOverlap = {
        def saveResult(path: String, p: Prototype[Array[Double]], header: String, enabled: Boolean) =
          evalOverlapEnd -- (saveToTable(path, p, header) when Condition(s"${p.name}Valid")) >-
            demux(s"Save${p.name.capitalize}Demux", regId, parId) -- finalizeTable(path, enabled)
        saveResult(dscValuesCsvPath, dscValues, header = labels, enabled = dscEnabled) +
        saveResult(dscGrpAvgCsvPath, dscGrpAvg, header = groups, enabled = dscEnabled) +
        saveResult(dscGrpStdCsvPath, dscGrpStd, header = groups, enabled = dscEnabled) +
        saveResult(jsiValuesCsvPath, jsiValues, header = labels, enabled = jsiEnabled) +
        saveResult(jsiGrpAvgCsvPath, jsiGrpAvg, header = groups, enabled = jsiEnabled) +
        saveResult(jsiGrpStdCsvPath, jsiGrpStd, header = groups, enabled = jsiEnabled)
      }
      // Append mean of overlap measures to summary CSV tables
      def saveMeanOverlap = {
        def saveMean(path: String, result: Prototype[Array[Double]], resultValid: Prototype[Boolean],
                               mean:   Prototype[Array[Double]], meanValid:   Prototype[Boolean], enabled: Boolean) =
          evalOverlapEnd >- calcMean(result, resultValid, mean, meanValid) -- Switch(
            Case(s" ${meanValid.name}", saveToSummaryTable(path, mean, header = groups)),
            Case(s"!${meanValid.name} && $enabled", Display.WARN(s"Invalid ${mean.name.capitalize} for $avgSet"))
          )
        saveMean(dscRegAvgCsvPath, dscGrpAvg, dscGrpAvgValid, dscRegAvg, dscRegAvgValid, enabled = dscEnabled) +
        saveMean(jsiRegAvgCsvPath, jsiGrpAvg, jsiGrpAvgValid, jsiRegAvg, jsiRegAvgValid, enabled = jsiEnabled)
      }
      // TODO: Create PNG snapshots of segmentation overlay on top of target image for visual assessment
      // Assemble sub-workflow
      (init -- warpSrcSeg) + (evalOverlap + saveOverlap + saveMeanOverlap)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TODO: Sort summary result tables when all results are appended

    // -----------------------------------------------------------------------------------------------------------------
    // TODO: Deform grid generated by Init workflow
    // TODO: Create PNG snapshots of deformed grid for visual assessment of deformation

    // -----------------------------------------------------------------------------------------------------------------
    // Calculate map of Jacobian determinants for qualitative assessment of volume changes and invertibility
    def evaluateJacobian = if (!keepOutJac) nop("CalcDetJac") else {
      registerImagesEnd -- ComputeJacobian(reg, regId, parId, tgtId, srcId, tgtImPath, outDof, outJac, outJacPath)
      // TODO: Compute statistics of Jacobian determinant and store these in CSV file
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Assemble complete registration evaluation workflow
    backupTables + convertDofin + registerImages + evaluateSimilarity + evaluateOverlap + evaluateJacobian
  }
}
