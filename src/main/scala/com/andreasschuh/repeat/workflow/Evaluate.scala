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

//import java.nio.file.{Path, Paths}
//
//import scala.language.reflectiveCalls
//
//import com.andreasschuh.repeat.core.{Environment => Env, _}
//import com.andreasschuh.repeat.puzzle._
//
//import org.openmole.core.dsl._
//import org.openmole.core.workflow.data.Prototype
//import org.openmole.core.workflow.transition.Condition
//import org.openmole.plugin.grouping.batch._
//import org.openmole.plugin.hook.file._
//import org.openmole.plugin.sampling.combine._
//import org.openmole.plugin.sampling.csv._
//import org.openmole.plugin.task.scala._
//import org.openmole.plugin.tool.pattern.{Skip, Switch, Case}
//
//
///**
// * Run registration with different parameters and store results for evaluation
// */
//object Evaluate {
//
//  /**
//   * Construct workflow puzzle
//   *
//   * @param reg Registration info
//   *
//   * @return Workflow puzzle for running the registration and generating the results needed for quality assessment
//   */
//  def apply(reg: Registration) = {
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Constants
//    import Dataset.{imgDir => _, segDir => _, _}
//    import Workspace.{imgDir, segDir, dofAff, dofPre, dofSuf, logDir, logSuf}
//    import Overlap.{labels, groups}
//    import Registration.times
//
//    // Input/intermediate files
//    val tgtImPath  = Paths.get(    imgDir.getAbsolutePath, imgPre + "${tgtId}"          +     imgSuf).toString
//    val srcImPath  = Paths.get(    imgDir.getAbsolutePath, imgPre + "${srcId}"          +     imgSuf).toString
//    val tgtSegPath = Paths.get(    segDir.getAbsolutePath, segPre + "${tgtId}"          +     segSuf).toString
//    val srcSegPath = Paths.get(    segDir.getAbsolutePath, segPre + "${srcId}"          +     segSuf).toString
//    val iniDofPath = Paths.get(    dofAff.getAbsolutePath, dofPre + "${tgtId},${srcId}" +     dofSuf).toString
//    val affDofPath = Paths.get(reg.affDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.affSuf).toString
//    val outDofPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.dofSuf).toString
//    val invDofPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${srcId},${tgtId}" + reg.dofSuf).toString
//    val a2bDofPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${tgtId},${midId}" + reg.dofSuf).toString
//    val b2cDofPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${midId},${srcId}" + reg.dofSuf).toString
//    val c2aDofPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${srcId},${tgtId}" + reg.dofSuf).toString
//    val outJacPath = Paths.get(reg.jacDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.jacSuf).toString
//    val outImPath  = Paths.get(reg.imgDir.getAbsolutePath, imgPre + "${srcId}-${tgtId}" +     imgSuf).toString
//    val outSegPath = Paths.get(reg.segDir.getAbsolutePath, segPre + "${srcId}-${tgtId}" +     segSuf).toString
//    val iceImPath  = Paths.get(reg.resDir.getAbsolutePath, "CICE", imgPre + "${tgtId},${srcId}" + imgSuf).toString
//    val teImPath   = Paths.get(reg.resDir.getAbsolutePath, "CTE", imgPre + "${tgtId},${midId},${srcId}" + imgSuf).toString
//    val outLogPath = Paths.get(    logDir.getAbsolutePath, "${regId}-${parId}", "${tgtId},${srcId}" + logSuf).toString
//
//    // Evaluation result files
//    def resTable(name: String) = Paths.get(reg.resDir.getAbsolutePath, name + ".csv").toString
//    def avgTable(name: String) = Paths.get(reg.sumDir.getAbsolutePath, name + ".csv").toString
//
//    val runTimeCsvPath = resTable("Time")
//    val avgTimeCsvPath = avgTable("Time")
//
//    val outSimCsvPath = resTable("Similarity")
//    val avgSimCsvPath = avgTable("Similarity")
//
//    val dscValuesCsvPath = resTable("DSC_Label")
//    val dscValAvgCsvPath = avgTable("DSC_Label_Mean")
//    val dscValStdCsvPath = avgTable("DSC_Label_Sigma")
//    val dscGrpAvgCsvPath = resTable("DSC_Group")
//    val dscGrpStdCsvPath = resTable("DSC_GroupSD")
//    val dscRegAvgCsvPath = avgTable("DSC_Group_Mean")
//
//    val jsiValuesCsvPath = resTable("JSI_Label")
//    val jsiValAvgCsvPath = avgTable("JSI_Label_Mean")
//    val jsiValStdCsvPath = avgTable("JSI_Label_Sigma")
//    val jsiGrpAvgCsvPath = resTable("JSI_Group")
//    val jsiGrpStdCsvPath = resTable("JSI_GroupSD")
//    val jsiRegAvgCsvPath = avgTable("JSI_Group_Mean")
//
//    val jacValuesCsvPath = resTable("Jacobian")
//    val jacLogValCsvPath = resTable("LogJacobian")
//
//    // Which intermediate result files to keep
//    val keepOutDof = true
//    val keepOutIm  = true
//    val keepOutSeg = true
//    val keepOutJac = true
//
//    // Which evaluation measures to compute
//    val timeEnabled = true
//    val simEnabled  = Array("SSD", "CC", "MI", "NMI")
//    val dscEnabled  = Overlap.measures contains Overlap.DSC
//    val jsiEnabled  = Overlap.measures contains Overlap.JSI
//    val jacEnabled  = Array("Normal distribution", "Extrema", "5th%", "95th%", "Mean <5th%", "Mean >95th%")
//    val jacHeader   = jacEnabled.flatMap(name => name.toLowerCase match {
//      case "extrema" => Array("Min", "Max")
//      case "normal distribution" | "mean+sigma" => Array("Mean", "Sigma")
//      case _ => Array(name)
//    })
//
//    val regSet = "{regId=${regId}, parId=${parId}, tgtId=${tgtId}, srcId=${srcId}}"
//    val avgSet = "{regId=${regId}, parId=${parId}}"
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Variables
//
//    // Input/output variables
//    val regId   = Val[String]              // ID of registration
//    val parIdx  = Val[Int]                 // Row index of parameter set
//    val parId   = Val[String]              // ID of parameter set
//    val parVal  = Val[Map[String, String]] // Map from parameter name to value
//    val tgtId   = Val[Int]                 // ID of target image
//    val tgtIm   = Val[Path]                // Fixed target image
//    val tgtSeg  = Val[Path]                // Segmentation of target image
//    val srcId   = Val[Int]                 // ID of source image
//    val srcIm   = Val[Path]                // Moving source image
//    val srcSeg  = Val[Path]                // Segmentation of source image
//    val iniDof  = Val[Path]                // Pre-computed affine transformation from target to source
//    val affDof  = Val[Path]                // Affine transformation converted to input format
//    val outDof  = Val[Path]                // Output transformation converted to IRTK format
//    val outIm   = Val[Path]                // Deformed source image
//    val outSeg  = Val[Path]                // Deformed source segmentation
//    val outJac  = Val[Path]                // Jacobian determinant map
//    val outLog  = Val[Path]                // Registration command output log file
//
//    // Evaluation results
//    val runTime = Val[Array[Double]]       // Runtime of registration command
//    val avgTime = Val[Array[Double]]       // Mean runtime over all registrations for a given set of parameters
//
//    val outSim = Val[Array[Double]]        // Intensity similarity of output image compared to target image
//    val avgSim = Val[Array[Double]]        // Average intensity similarity of output images
//
//    val dscValues = Val[Array[Double]]     // Dice similarity coefficient (DSC) for each label and segmentation
//    val dscValAvg = Val[Array[Double]]     // Mean DSC for each label across all pairwise registrations with common parId
//    val dscValStd = Val[Array[Double]]     // Standard deviation of DSC for each label across all pairwise registrations
//    val dscGrpAvg = Val[Array[Double]]     // Mean DSC for each label group and segmentation
//    val dscGrpStd = Val[Array[Double]]     // Standard deviation of DSC for each label group and segmentation
//    val dscRegAvg = Val[Array[Double]]     // Average mean DSC for a given set of registration parameters
//
//    val jsiValues = Val[Array[Double]]     // Jaccard similarity index (JSI) for each label and segmentation
//    val jsiValAvg = Val[Array[Double]]     // Mean JSI for each label across all pairwise registrations with common parId
//    val jsiValStd = Val[Array[Double]]     // Standard deviation of JSI for each label across all pairwise registrations
//    val jsiGrpAvg = Val[Array[Double]]     // Mean JSI for each label group and segmentation
//    val jsiGrpStd = Val[Array[Double]]     // Standard deviation of JSI for each label group and segmentation
//    val jsiRegAvg = Val[Array[Double]]     // Average mean JSI for a given set of registration parameters
//
//    val jacValues = Val[Array[Double]]     // Jacobian determinant statistics
//    val jacLogVal = Val[Array[Double]]     // Logarithm of Jacobian determinant statistics
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Samplings
//    val paramSampling = CSVToMapSampling(reg.parCsv, parVal)
//    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
//    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
//    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Auxiliaries
//
//    // NOP
//    def nopCap(taskName: String) =
//      Capsule(
//        EmptyTask() set (
//          name := s"${reg.id}-$taskName-NOP"
//        )
//      )
//    def nop(taskName: String) = nopCap(taskName).toPuzzle
//
//    // Exploration tasks
//    def putRegId =
//      EmptyTask() set (
//        name    := s"${reg.id}-SetRegId",
//        outputs += regId,
//        regId   := reg.id
//      )
//
//    def forEachPar =
//      ExplorationTask(paramSampling zipWithIndex parIdx) set (
//        name    := s"${reg.id}-ForEachPar",
//        inputs  += regId,
//        outputs += regId
//      )
//
//    def putParId =
//      ScalaTask(
//        """
//          | val parId  = input.parVal.getOrElse("ID", f"$parIdx%02d")
//          | val parVal = input.parVal - "ID"
//        """.stripMargin
//      ) set (
//        name    := s"${reg.id}-SetParId",
//        inputs  += (regId, parIdx, parVal),
//        outputs += (regId, parId, parVal)
//      )
//
//    def demux(taskName: String, input: Prototype[_]*) = {
//      val inputNames = input.toSeq.map(_.name)
//      val task =
//        ScalaTask(inputNames.map(name => s"val $name = input.$name.head").mkString("\n")) set (
//          name := s"${reg.id}-$taskName"
//        )
//      input.foreach(p => {
//        task.addInput(p.toArray)
//        task.addOutput(p)
//      })
//      Capsule(task)
//    }
//
//    def forEachImPair =
//      ExplorationTask(imageSampling) set (
//        name    := s"${reg.id}-ForEachImPair",
//        inputs  += regId,
//        outputs += regId
//      )
//
//    def forEachTarget =
//      ExplorationTask(tgtIdSampling) set (
//        name    := s"${reg.id}-ForEachTarget",
//        inputs  += regId,
//        outputs += regId
//      )
//
//    def end(taskName: String) =
//      Capsule(
//        EmptyTask() set (
//          name    := s"${reg.id}-$taskName",
//          inputs  += regId,
//          outputs += regId
//        ),
//        strainer = true
//      )
//
//    // Task factory
//    val tasks = Tasks(reg, puzzleName = "Evaluate")
//
//    // Delete table with summary results which can be recomputed from individual result tables
//    def deleteTable(path: String, enabled: Boolean) = tasks.deleteTable(path, enabled)
//    // Make copy of previous result tables and merge them with previously copied results to ensure none are lost
//    def backupTable(path: String, enabled: Boolean) = tasks.backupTable(path, enabled)
//    // Read previous result from backup table to save re-computation if nothing changed
//    def readFromTable(path: String, columns: Seq[_], values: Prototype[Array[Double]], enabled: Boolean = true) =
//      tasks.readFromTable(path, columns, values, enabled)
//    // Calculate mean of values over all registration results computed with a fixed set of parameters
//    def calcMean(result: Prototype[Array[Double]], mean: Prototype[Array[Double]]) = tasks.getMean(result, mean)
//    // Calculate standard deviation of values over all registration results computed with a fixed set of parameters
//    def calcSDev(result: Prototype[Array[Double]], sigma: Prototype[Array[Double]]) = tasks.getSD(result, sigma)
//    // Calculate mean and standard deviation of values over all registration results computed with a fixed set of parameters
//    def calcMeanAndSDev(result: Prototype[Array[Double]], mean: Prototype[Array[Double]], sigma: Prototype[Array[Double]]) =
//      tasks.getMeanAndSD(result, mean, sigma)
//    // Write individual registration result to CSV table
//    def saveToTable(path: String, header: Seq[_], result: Prototype[Array[Double]]) = tasks.saveToTable(path, header, result)
//    // Write mean values calculated over all registration results computed with a fixed set of parameters to CSV table
//    def saveToSummary(path: String, header: Seq[_], mean: Prototype[Array[Double]]) = tasks.saveToSummary(path, header, mean)
//    // Finalize result table, appending non-overwritten previous results again and sorting the final table
//    def finalizeTable(path: String, enabled: Boolean = true) = tasks.finalizeTable(path, enabled)
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Backup previous result tables
//    val backupTablesEnd = demux("BackupTablesEnd", regId)
//    def backupTables =
//      putRegId --
//      // Delete summary tables with mean values
//      deleteTable(avgTimeCsvPath,   timeEnabled && !Workspace.append) --
//      deleteTable(dscValAvgCsvPath, dscEnabled  && !Workspace.append) --
//      deleteTable(dscValStdCsvPath, dscEnabled  && !Workspace.append) --
//      deleteTable(dscRegAvgCsvPath, dscEnabled  && !Workspace.append) --
//      deleteTable(jsiValAvgCsvPath, jsiEnabled  && !Workspace.append) --
//      deleteTable(jsiValStdCsvPath, jsiEnabled  && !Workspace.append) --
//      deleteTable(jsiRegAvgCsvPath, jsiEnabled  && !Workspace.append) --
//      // Results of individual registrations
//      forEachPar -< putParId --
//        backupTable(runTimeCsvPath,   timeEnabled) --
//        backupTable(dscValuesCsvPath, dscEnabled) --
//        backupTable(dscGrpAvgCsvPath, dscEnabled) --
//        backupTable(dscGrpStdCsvPath, dscEnabled) --
//        backupTable(jsiValuesCsvPath, jsiEnabled) --
//        backupTable(jsiGrpAvgCsvPath, jsiEnabled) --
//        backupTable(jsiGrpStdCsvPath, jsiEnabled) >-
//      backupTablesEnd
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Run registration command for each image pair in dataset
//    val registerImagesEnd = end("RegisterImagesEnd")
//    def registerImages = {
//      // Execute pairwise registration
//      val regCond =
//        Condition(
//          s"""
//            | val tgtIm  = input.tgtIm.toFile
//            | val srcIm  = input.srcIm.toFile
//            | val iniDof = new java.io.File(s"$iniDofPath")
//            | val outDof = new java.io.File(s"$outDofPath")
//            | val outIm  = new java.io.File(s"$outImPath")
//            | val outSeg = new java.io.File(s"$outSegPath")
//            | val outJac = new java.io.File(s"$outJacPath")
//            |
//            | def updateOutDof = $keepOutDof &&
//            |   outDof.lastModified < iniDof.lastModified &&
//            |   outDof.lastModified < tgtIm .lastModified &&
//            |   outDof.lastModified < srcIm .lastModified
//            | def updateOutIm = $keepOutIm &&
//            |   outIm.lastModified < iniDof.lastModified &&
//            |   outIm.lastModified < tgtIm .lastModified &&
//            |   outIm.lastModified < srcIm .lastModified
//            | def updateOutSeg = $keepOutSeg &&
//            |   outSeg.lastModified < iniDof.lastModified &&
//            |   outSeg.lastModified < tgtIm .lastModified &&
//            |   outSeg.lastModified < srcIm .lastModified
//            | def updateOutJac = $keepOutJac &&
//            |   outJac.lastModified < iniDof.lastModified &&
//            |   outJac.lastModified < tgtIm .lastModified &&
//            |   outJac.lastModified < srcIm .lastModified
//            |
//            | updateOutDof || (!outDof.exists && (updateOutIm || updateOutSeg || updateOutJac)) || ($timeEnabled && runTime.isEmpty)
//          """.stripMargin
//        )
//      // Prepare input transformation
//      val convertDofinEnd = demux("ConvertDofinEnd", regId)
//      def convertDofin = {
//        val begin =
//          Capsule(
//            ScalaTask(
//              s"""
//                | val iniDof = Paths.get(s"$iniDofPath")
//              """.stripMargin
//            ) set (
//              name    := s"${reg.id}-ConvertDofinBegin",
//              imports += "java.nio.file.Paths",
//              inputs  += (regId, tgtId, srcId),
//              outputs += (regId, tgtId, srcId, iniDof)
//            )
//          )
//        backupTablesEnd -- Capsule(forEachImPair, strainer = true) -< begin --
//          ConvertDofToAff(reg, regId, tgtId, srcId, iniDof, affDof, affDofPath) >-
//        convertDofinEnd
//      }
//      // Run direct registration between image pairs
//      def directRegistration = {
//        val begin =
//          Capsule(
//            ScalaTask(
//              s"""
//                | val tgtIm  = Paths.get(s"$tgtImPath")
//                | val srcIm  = Paths.get(s"$srcImPath")
//                | val affDof = Paths.get(s"$affDofPath")
//                | val outDof = Paths.get(s"$outDofPath")
//                | val outLog = Paths.get(s"$outLogPath")
//              """.stripMargin
//            ) set (
//              name    := s"${reg.id}-RegisterImagesBegin",
//              imports += "java.nio.file.Paths",
//              inputs  += (regId, parId, tgtId, srcId),
//              outputs += (regId, parId, tgtId, srcId, tgtIm, srcIm, affDof, outDof, outLog)
//            ),
//            strainer = true
//          )
//        val task =
//          RegisterImages(reg, regId, parId, parVal, tgtId, srcId, tgtIm, srcIm, affDof, outDof, outLog, runTime)
//        convertDofinEnd -- forEachPar -< putParId --
//          Capsule(forEachImPair, strainer = true) -< begin --
//            readFromTable(runTimeCsvPath, times, runTime, enabled = timeEnabled) --
//            Switch(
//              Case( regCond, Display.QSUB(s"Registration for $regSet") -- (task on reg.runEnv) -- Display.DONE(s"Registration for $regSet")),
//              Case(!regCond, Display.SKIP(s"Registration for $regSet"))
//            ) --
//        registerImagesEnd
//      }
//      // Write runtime measurements
//      def saveTime =
//        registerImagesEnd -- Switch(
//          Case( "runTime.nonEmpty",                saveToTable(runTimeCsvPath, times, runTime)),
//          Case(s"runTime.isEmpty && $timeEnabled", Display.WARN(s"Missing ${runTime.name.capitalize} for $regSet"))
//        ) >- demux("SaveTimeDemux", regId, parId) -- finalizeTable(runTimeCsvPath, timeEnabled)
//      // Write mean of runtime measurements
//      def saveMeanTime =
//        registerImagesEnd >-
//          calcMean(runTime, avgTime) -- Switch(
//            Case( "avgTime.nonEmpty",                saveToSummary(avgTimeCsvPath, times, avgTime)),
//            Case(s"avgTime.isEmpty && $timeEnabled", Display.WARN(s"Invalid ${avgTime.name.capitalize} for $avgSet"))
//          )
//      // Assemble sub-workflow
//      convertDofin + directRegistration + saveTime + saveMeanTime
//    }
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // TODO: Register each image in dataset to (custom) template
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Deform source image and quantify residual differences between fixed target and deformed source image
//    def evaluateSimilarity = if (!keepOutIm && simEnabled.isEmpty) nop("EvaluateSimilarity") else {
//      // Initialize file paths and read previously computed results from backup table
//      val outImChg = Val[Boolean]
//      def init =
//        registerImagesEnd --
//        Capsule(
//          ScalaTask(
//            s"""
//              | val tgtIm = Paths.get(s"$tgtImPath")
//              | val srcIm = Paths.get(s"$srcImPath")
//              | val outIm = Paths.get(s"$outImPath")
//            """.stripMargin
//          ) set (
//            name     := s"${reg.id}-EvaluateSimilarityInit",
//            imports  += "java.nio.file.Paths",
//            inputs   += (regId, parId, tgtId, srcId, outDof),
//            outputs  += (regId, parId, tgtId, srcId, outDof, tgtIm, srcIm, outIm, outImChg),
//            outImChg := false
//          )
//        ) --
//        readFromTable(outSimCsvPath, simEnabled, outSim, enabled = simEnabled.nonEmpty)
//      val noPrevRes = Condition("outSim.isEmpty")
//      // Transform and resample source image
//      val warpSrcImEnd = end("WarpSrcImEnd")
//      def warpSrcIm = {
//        val what = "Warp source image for " + regSet
//        val template = Val[Cmd]
//        val cond = {
//          val update =
//            Condition(
//              s"""
//                | outIm.toFile.lastModified < tgtIm.toFile.lastModified ||
//                | outIm.toFile.lastModified < srcIm.toFile.lastModified ||
//                | ($keepOutDof && outIm.toFile.lastModified < outDof.toFile.lastModified)
//              """.stripMargin
//            )
//          if (keepOutIm) update else noPrevRes && update
//        }
//        val task =
//          Capsule(
//            ScalaTask(
//              """
//                | val args = Map(
//                |   "target" -> tgtIm .toString,
//                |   "source" -> srcIm .toString,
//                |   "out"    -> outIm .toString,
//                |   "phi"    -> outDof.toString
//                | )
//                | val cmd = command(template, args)
//                | val outDir = outIm.getParent
//                | if (outDir != null) Files.createDirectories(outDir)
//                | val ret = cmd.!
//                | if (ret != 0) {
//                |   val str = cmd.mkString("\"", "\" \"", "\"\n")
//                |   throw new Exception("Image deformation command returned non-zero exit code: " + str)
//                | }
//              """.stripMargin
//            ) set (
//              name        := s"${reg.id}-WarpSrcIm",
//              imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._", "java.nio.file.Files"),
//              usedClasses += Registration.getClass,
//              inputs      += (regId, parId, tgtId, srcId, tgtIm, srcIm, outIm, outDof, template),
//              outputs     += (regId, parId, tgtId, srcId, tgtIm, outIm),
//              template    := reg.deformImageCmd,
//              outImChg    := true
//            ),
//            strainer = true
//          )
//        Switch(
//          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
//          Case(!cond, Display.SKIP(what))
//        ) -- warpSrcImEnd
//      }
//      // Evaluate residual difference between target and output image
//      val evalSimEnd = end("EvalSimEnd")
//      def evalSim = if (simEnabled.isEmpty) nop("EvalSim") else {
//        val what = "Similarity evaluation for " + regSet
//        val cond = noPrevRes || "outImChg"
//        val task =
//          Capsule(
//            ScalaTask(
//              s"""
//                | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
//                | val hdr = Array[String](${if (simEnabled.isEmpty) "" else "\"" + simEnabled.mkString("\", \"") + "\""})
//                | val sim = IRTK.similarity(tgtIm.toFile, outIm.toFile)
//                | val outSim = hdr.map(sim(_))
//              """.stripMargin
//            ) set (
//              name        := s"${reg.id}-EvalSim",
//              imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Prefix}",
//              usedClasses += (Config.getClass, IRTK.getClass, Prefix.getClass),
//              inputs      += (regId, parId, tgtId, srcId, tgtIm, outIm),
//              outputs     += (regId, parId, tgtId, srcId, outSim)
//            )
//          )
//        warpSrcImEnd -- Switch(
//          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
//          Case(!cond, Display.SKIP(what))
//        ) -- evalSimEnd
//      }
//      // Save image similarity measures to CSV table
//      def saveSim =
//        evalSimEnd -- saveToTable(outSimCsvPath, simEnabled, outSim) >-
//          demux("SaveSimDemux", regId, parId) -- finalizeTable(outSimCsvPath, simEnabled.nonEmpty)
//      def saveMeanSim =
//        evalSimEnd >-
//          calcMean(outSim, avgSim) -- Switch(
//            Case( "avgSim.nonEmpty", saveToSummary(avgSimCsvPath, simEnabled, avgSim)),
//            Case(s"avgSim.isEmpty && ${simEnabled.nonEmpty}", Display.WARN(s"Invalid ${avgSim.name.capitalize} for $avgSet"))
//          )
//      // TODO: Create PNG snapshot of side-by-side view of target and output image
//      def snapComp = nop("SnapComp")
//      // TODO: Create PNG snapshot of difference image
//      def snapDiff = nop("SnapDiff")
//      // Assemble sub-workflow
//      (init -- warpSrcIm) + (evalSim + saveSim + saveMeanSim) + (snapComp + snapDiff)
//    }
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Deform source segmentation and quantify overlap with target labels
//    def evaluateOverlap = if (!keepOutSeg && !dscEnabled && !jsiEnabled) nop("EvaluateOverlap") else {
//      // Initialize file paths and read previously computed results from backup tables
//      val outSegChg = Val[Boolean]
//      def init =
//        registerImagesEnd --
//        Capsule(
//          ScalaTask(
//            s"""
//              | val tgtSeg = Paths.get(s"$tgtSegPath")
//              | val srcSeg = Paths.get(s"$srcSegPath")
//              | val outSeg = Paths.get(s"$outSegPath")
//            """.stripMargin
//          ) set (
//            name      := s"${reg.id}-EvaluateOverlapInit",
//            imports   += "java.nio.file.Paths",
//            inputs    += (regId, parId, tgtId, srcId, outDof),
//            outputs   += (regId, parId, tgtId, srcId, outDof, tgtSeg, srcSeg, outSeg, outSegChg),
//            outSegChg := false
//          )
//        ) --
//        readFromTable(dscValuesCsvPath, labels, dscValues, enabled = dscEnabled) --
//        readFromTable(dscGrpAvgCsvPath, groups, dscGrpAvg, enabled = dscEnabled) --
//        readFromTable(dscGrpStdCsvPath, groups, dscGrpStd, enabled = dscEnabled) --
//        readFromTable(jsiValuesCsvPath, labels, jsiValues, enabled = jsiEnabled) --
//        readFromTable(jsiGrpAvgCsvPath, groups, jsiGrpAvg, enabled = jsiEnabled) --
//        readFromTable(jsiGrpStdCsvPath, groups, jsiGrpStd, enabled = jsiEnabled)
//      val noPrevRes =
//        Condition(
//          s"""
//            | ($dscEnabled && (dscValues.isEmpty || dscGrpAvg.isEmpty || dscGrpStd.isEmpty)) ||
//            | ($jsiEnabled && (jsiValues.isEmpty || jsiGrpAvg.isEmpty || jsiGrpStd.isEmpty))
//          """.stripMargin
//        )
//      // Deform source segmentation
//      val warpSrcSegEnd = end("WarpSrcSegEnd")
//      def warpSrcSeg = {
//        val what = "Propagate source labels for " + regSet
//        val template = Val[Cmd]
//        val cond = {
//          val update =
//            Condition(
//              s"""
//                | outSeg.toFile.lastModified < tgtSeg.toFile.lastModified ||
//                | outSeg.toFile.lastModified < srcSeg.toFile.lastModified ||
//                | ($keepOutDof && outSeg.toFile.lastModified < outDof.toFile.lastModified)
//              """.stripMargin
//            )
//          if (keepOutSeg) update else noPrevRes && update
//        }
//        val task =
//          Capsule(
//            ScalaTask(
//              """
//                | val args = Map(
//                |   "target" -> tgtSeg.toString,
//                |   "source" -> srcSeg.toString,
//                |   "out"    -> outSeg.toString,
//                |   "phi"    -> outDof.toString
//                | )
//                | val cmd = command(template, args)
//                | val outDir = outSeg.getParent
//                | if (outDir != null) Files.createDirectories(outDir)
//                | val ret = cmd.!
//                | if (ret != 0) {
//                |   val str = cmd.mkString("\"", "\" \"", "\"\n")
//                |   throw new Exception("Label propagation command returned non-zero exit code: " + str)
//                | }
//              """.stripMargin
//            ) set (
//              name        := s"${reg.id}-PropagateLabels",
//              imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._", "java.nio.file.Files"),
//              usedClasses += Registration.getClass,
//              inputs      += (regId, parId, tgtId, srcId, tgtSeg, srcSeg, outDof, outSeg, template),
//              outputs     += (regId, parId, tgtId, srcId, tgtSeg, outSeg, outSegChg),
//              template    := reg.deformLabelsCmd,
//              outSegChg   := true
//            ),
//            strainer = true
//          )
//        Switch(
//          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
//          Case(!cond, Display.SKIP(what))
//        ) -- warpSrcSegEnd
//      }
//      // Evaluate overlap measures
//      val evalOverlapEnd = end("EvalOverlapEnd")
//      def evalOverlap = if (!dscEnabled && !jsiEnabled) nop("EvalOverlap") else {
//        val what = s"Overlap evaluation for $regSet"
//        val cond = noPrevRes || "outSegChg"
//        val task =
//          Capsule(
//            ScalaTask(
//              s"""
//                | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
//                | val stats = IRTK.labelStats(tgtSeg.toFile, outSeg.toFile, Some(Overlap.labels.toSet))
//                |
//                | val dscMetric = Overlap(stats, Overlap.DSC)
//                | val dscValues = dscMetric.toArray
//                | val dscGrpAvg = dscMetric.getMeanValues
//                | val dscGrpStd = dscMetric.getSigmaValues
//                |
//                | val jsiMetric = Overlap(stats, Overlap.JSI)
//                | val jsiValues = jsiMetric.toArray
//                | val jsiGrpAvg = jsiMetric.getMeanValues
//                | val jsiGrpStd = jsiMetric.getSigmaValues
//              """.stripMargin
//            ) set (
//              name        := s"${reg.id}-EvaluateOverlap",
//              imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Overlap}",
//              usedClasses += (Config.getClass, IRTK.getClass, Overlap.getClass),
//              inputs      += (regId, parId, tgtId, srcId, tgtSeg, outSeg),
//              outputs     += (regId, parId, tgtId, srcId, dscValues, dscGrpAvg, dscGrpStd, jsiValues, jsiGrpAvg, jsiGrpStd)
//            )
//          )
//        warpSrcSegEnd -- Switch(
//          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
//          Case(!cond, Display.SKIP(what))
//        ) -- evalOverlapEnd
//      }
//      // Save overlap measures to CSV tables
//      def saveOverlap = {
//        def saveResult(path: String, header: Seq[_], result: Prototype[Array[Double]], enabled: Boolean) =
//          evalOverlapEnd -- (saveToTable(path, header, result) when Condition(s"${result.name}.nonEmpty")) >-
//            demux(s"Save${result.name.capitalize}Demux", regId, parId) -- finalizeTable(path, enabled)
//        saveResult(dscValuesCsvPath, labels, dscValues, enabled = dscEnabled) +
//        saveResult(dscGrpAvgCsvPath, groups, dscGrpAvg, enabled = dscEnabled) +
//        saveResult(dscGrpStdCsvPath, groups, dscGrpStd, enabled = dscEnabled) +
//        saveResult(jsiValuesCsvPath, labels, jsiValues, enabled = jsiEnabled) +
//        saveResult(jsiGrpAvgCsvPath, groups, jsiGrpAvg, enabled = jsiEnabled) +
//        saveResult(jsiGrpStdCsvPath, groups, jsiGrpStd, enabled = jsiEnabled)
//      }
//      def saveMeanOverlap = {
//        def saveMean(path: String, header: Seq[_], result: Prototype[Array[Double]], mean: Prototype[Array[Double]], enabled: Boolean) =
//          evalOverlapEnd >- calcMean(result, mean) -- Switch(
//            Case(s"${mean.name}.nonEmpty",            saveToSummary(path, header, mean)),
//            Case(s"${mean.name}.isEmpty && $enabled", Display.WARN(s"Invalid ${mean.name.capitalize} for $avgSet"))
//          )
//        def saveSDev(path: String, header: Seq[_], result: Prototype[Array[Double]], sigma: Prototype[Array[Double]], enabled: Boolean) =
//          evalOverlapEnd >- calcSDev(result, sigma) -- Switch(
//            Case(s"${sigma.name}.nonEmpty",            saveToSummary(path, header, sigma)),
//            Case(s"${sigma.name}.isEmpty && $enabled", Display.WARN(s"Invalid ${sigma.name.capitalize} for $avgSet"))
//          )
//        saveMean(dscValAvgCsvPath, labels, dscValues, dscValAvg, enabled = dscEnabled) +
//        saveSDev(dscValStdCsvPath, labels, dscValues, dscValStd, enabled = dscEnabled) +
//        saveMean(dscRegAvgCsvPath, groups, dscGrpAvg, dscRegAvg, enabled = dscEnabled) +
//        saveMean(jsiValAvgCsvPath, labels, jsiValues, jsiValAvg, enabled = jsiEnabled) +
//        saveSDev(jsiValStdCsvPath, labels, jsiValues, jsiValStd, enabled = jsiEnabled) +
//        saveMean(jsiRegAvgCsvPath, groups, jsiGrpAvg, jsiRegAvg, enabled = jsiEnabled)
//      }
//      // TODO: Create PNG snapshots of segmentation overlay on top of target image for visual assessment
//      //       for registration result with worst, best, and average mean overlap (i.e., Dice coefficient)
//      // Assemble sub-workflow
//      (init -- warpSrcSeg) + (evalOverlap + saveOverlap + saveMeanOverlap)
//    }
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // TODO: Sort summary result tables when all results are appended
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // TODO: Deform grid generated by Init workflow
//    // TODO: Create PNG snapshots of deformed grid for visual assessment of deformation
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Calculate map of Jacobian determinants for qualitative assessment of volume changes and invertibility
//    def evaluateJacobian = if (!keepOutJac && jacEnabled.isEmpty) nop("EvaluateJacobian") else {
//      // Initialize file paths and read previously computed results from backup tables
//      val outJacChg = Val[Boolean]
//      def init =
//        registerImagesEnd --
//          Capsule(
//            ScalaTask(
//              s"""
//                | val outJac = Paths.get(s"$outJacPath")
//              """.stripMargin
//            ) set (
//              name      := s"${reg.id}-EvaluateJacobianInit",
//              imports   += "java.nio.file.Paths",
//              inputs    += (regId, parId, tgtId, srcId, outDof),
//              outputs   += (regId, parId, tgtId, srcId, outDof, outJac, outJacChg),
//              outJacChg := false
//            )
//          ) --
//          readFromTable(jacValuesCsvPath, jacHeader, jacValues, enabled = jacEnabled.nonEmpty) --
//          readFromTable(jacLogValCsvPath, jacHeader, jacLogVal, enabled = jacEnabled.nonEmpty)
//      val noPrevJacVal = Condition(s"$jacEnabled && jacValues.isEmpty")
//      val noPrevLogJac = Condition(s"$jacEnabled && jacLogVal.isEmpty")
//      // Compute Jacobian determinant map
//      val calcJacEnd = end("CalcJacEnd")
//      def calcJac = {
//        val what = "Computing Jacobian map for " + regSet
//        val template = Val[Cmd]
//        val cond =
//          if (keepOutJac) Condition("outJac.toFile.lastModified < outDof.toFile.lastModified")
//          else noPrevJacVal || noPrevLogJac
//        val task =
//          ScalaTask(
//            s"""
//              | val args = Map(
//              |   "mask"   -> s"$tgtSegPath",
//              |   "target" -> s"$tgtImPath",
//              |   "phi"    -> outDof.toString,
//              |   "out"    -> outJac.toString
//              | )
//              | val cmd = command(template, args)
//              | val outDir = outJac.getParent
//              | if (outDir != null) java.nio.file.Files.createDirectories(outDir)
//              | val ret = cmd.!
//              | if (ret != 0) {
//              |   val str = cmd.mkString("\\"", "\\" \\"", "\\"\\n")
//              |   throw new Exception("Jacobian command returned non-zero exit code: " + str)
//              | }
//            """.stripMargin
//          ) set (
//            name        := s"${reg.id}-CalcJac",
//            imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._"),
//            usedClasses += Registration.getClass,
//            inputs      += (regId, parId, tgtId, srcId, outJac, outDof, template),
//            outputs     += (regId, parId, tgtId, srcId, outJac),
//            template    := reg.jacCmd
//          )
//        Switch(
//          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
//          Case(!cond, Display.SKIP(what))
//        ) -- calcJacEnd
//      }
//      // Calculate Jacobian determinant statistics
//      val evalJacEnd = end("EvalJacEnd")
//      def evalJac = if (jacEnabled.isEmpty) nop("EvalJac") else {
//        val what = "Jacobian evaluation for " + regSet
//        val cond = noPrevJacVal || "outJacChg"
//        val task =
//          Capsule(
//            ScalaTask(
//              s"""
//                | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
//                | val jacStats  = Array[String](${if (jacEnabled.isEmpty) "" else "\"" + jacEnabled.mkString("\", \"") + "\""})
//                | val jacHeader = Array[String](${if (jacHeader .isEmpty) "" else "\"" + jacHeader .mkString("\", \"") + "\""})
//                | val jacValues = jacHeader.map(IRTK.stats(outJac.toFile, which = jacHeader)(_))
//              """.stripMargin
//            ) set (
//              name        := s"${reg.id}-EvalJac",
//              imports     += "com.andreasschuh.repeat.core.{Config, IRTK}",
//              usedClasses += (Config.getClass, IRTK.getClass),
//              inputs      += (regId, parId, tgtId, srcId, outJac),
//              outputs     += (regId, parId, tgtId, srcId, jacValues)
//            )
//          )
//        /*
//        calcJacEnd -- Switch(
//          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
//          Case(!cond, Display.SKIP(what))
//        ) -- evalJacEnd
//        */
//        calcJacEnd -- (task on Env.short by 10) -- evalJacEnd
//      }
//      // Calculate log Jacobian determinant statistics
//      val evalLogJacEnd = end("EvalLogJacEnd")
//      def evalLogJac = if (jacEnabled.isEmpty) nop("EvalLogJac") else {
//        val what = "Log Jacobian evaluation for " + regSet
//        val cond = noPrevLogJac || "outJacChg"
//        val task =
//          Capsule(
//            ScalaTask(
//              s"""
//                | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
//                | val jacStats  = Array[String](${if (jacEnabled.isEmpty) "" else "\"" + jacEnabled.mkString("\", \"") + "\""})
//                | val jacHeader = Array[String](${if (jacHeader .isEmpty) "" else "\"" + jacHeader .mkString("\", \"") + "\""})
//                | val jacLogVal = jacHeader.map(IRTK.stats(outJac.toFile, which = jacStats, log = true)(_))
//              """.stripMargin
//            ) set (
//              name        := s"${reg.id}-EvalLogJac",
//              imports     += "com.andreasschuh.repeat.core.{Config, IRTK}",
//              usedClasses += (Config.getClass, IRTK.getClass),
//              inputs      += (regId, parId, tgtId, srcId, outJac),
//              outputs     += (regId, parId, tgtId, srcId, jacLogVal)
//            )
//          )
//        /*
//        calcJacEnd -- Switch(
//          Case( cond, Display.QSUB(what) -- (task on Env.short by 10) -- Display.DONE(what)),
//          Case(!cond, Display.SKIP(what))
//        ) -- evalLogJacEnd
//        */
//        calcJacEnd -- (task on Env.short by 10) -- evalLogJacEnd
//      }
//      // Save Jacobian determinant statistics to CSV table
//      def saveJac =
//        evalJacEnd -- saveToTable(jacValuesCsvPath, jacHeader, jacValues) >-
//          demux("SaveJacDemux", regId, parId) -- finalizeTable(jacValuesCsvPath, jacEnabled.nonEmpty)
//      def saveLogJac =
//        evalLogJacEnd -- saveToTable(jacLogValCsvPath, jacHeader, jacLogVal) >-
//          demux("SaveLogJacDemux", regId, parId) -- finalizeTable(jacLogValCsvPath, jacEnabled.nonEmpty)
//      // Assemble sub-workflow
//      (init -- calcJac) + (evalJac + saveJac) + (evalLogJac + saveLogJac)
//    }
//
//    // -----------------------------------------------------------------------------------------------------------------
//    // Assemble complete registration evaluation workflow
//    backupTables + registerImages + evaluateSimilarity + evaluateOverlap + evaluateJacobian
//  }
//}
