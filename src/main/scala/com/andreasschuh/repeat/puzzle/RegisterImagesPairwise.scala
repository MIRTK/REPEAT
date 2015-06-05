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
import java.nio.file.Path
import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.mole.{Capsule, Hook}
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.{Switch, Case}

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Workflow puzzle for pairwise registration of dataset images
 */
object PairwiseRegistration {

  /** Whether to save runtime of registrations */
  val saveTimes = true

  /** Whether to save resulting transformation files in workspace */
  val saveDofs = true

  /** Whether to save registration log files in workspace */
  val saveLogs = true

  /** Get workflow puzzle for pairwise registration of dataset images using the specified registration */
  def apply(reg: Registration) = new PairwiseRegistration(reg)
}

/**
 * Register all pairs of dataset images with one another
 *
 * @param reg Registration to use.
 */
class PairwiseRegistration(reg: Registration) {

  import Dataset.{imgCsv, imgPre, imgSuf, imgExt}
  import Workspace.{imgDir, dofAff, dofPre, dofSuf, logSuf}
  import PairwiseRegistration.{saveTimes, saveDofs, saveLogs}
  import Variables.{go, regId, parId, parIdx, parVal, tgtId, srcId}

  // Auxiliary functions and common task generators
  private val tasks = Tasks(reg, puzzleName = "PairwiseRegistration")

  val tgtImgPath = Val[Path]
  val tgtImgFile = Val[File]
  val srcImgPath = Val[Path]
  val srcImgFile = Val[File]
  val iniDofPath = Val[Path]
  val iniDofFile = Val[File]
  val outDofPath = Val[Path]
  val outDofFile = Val[File]
  val outLogPath = Val[Path]
  val outLogFile = Val[File]

  val runTime = Val[Array[Double]]
  val avgTime = Val[Array[Double]]
  val runTimeTable = tasks.resultTable("Time")
  val avgTimeTable = tasks.summaryTable("Time")

  /** Explore all parameter settings of the registration */
  private val forEachPar = {
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal)
    Capsule(
      ExplorationTask(paramSampling zipWithIndex parIdx) set (
        name    := s"PairwiseRegistration(${reg.id}).forEachPar",
        inputs  += regId,
        outputs += regId
      )
    )
  }

  /** Explore all pairs of images */
  private val forEachPair = {
    val tgtIdSampling = CSVSampling(imgCsv)
    val srcIdSampling = CSVSampling(imgCsv)
    tgtIdSampling.addColumn("Image ID", tgtId)
    srcIdSampling.addColumn("Image ID", srcId)
    Capsule(
      ExplorationTask((tgtIdSampling x srcIdSampling) filter "tgtId != srcId") set (
        name    := s"PairwiseRegistration(${reg.id}).forEachPair",
        inputs  += (regId, parId, parVal),
        outputs += (regId, parId, parVal)
      )
    )
  }

  /** Set paths of workflow puzzle input/output files */
  private val regSwitchBegin =
    Capsule(
      ScalaTask(
        s"""
          | val tgtImgPath = Paths.get(s"$imgDir", "$imgPre" + tgtId + "$imgSuf")
          | val srcImgPath = Paths.get(s"$imgDir", "$imgPre" + srcId + "$imgSuf")
          | val iniDofPath = Paths.get(s"$dofAff", "$dofPre" + tgtId + "," + srcId + "$dofSuf")
          | val outDofPath = Paths.get(s"${reg.dofDir}", "$dofPre" + tgtId + "," + srcId + "${reg.dofSuf}")
          | val outLogPath = Paths.get(s"${reg.logDir}", tgtId + "," + srcId + "$logSuf")
        """.stripMargin
      ) set (
        name    := s"PairwiseRegistration(${reg.id}).regSwitchBegin",
        imports += "java.nio.file.Paths",
        inputs  += (regId, parId, parVal, tgtId, srcId, runTime),
        outputs += (regId, parId, parVal, tgtId, srcId, runTime),
        outputs += (tgtImgPath, srcImgPath, iniDofPath, outDofPath, outLogPath)
      )
    )

  /** Register image pair */
  private val registerImages = {
    val dof2affCmd  = Val[Cmd]
    val registerCmd = Val[Cmd]
    val phi2dofCmd  = Val[Cmd]
    Capsule(
      ScalaTask(
        s"""
          | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
          |
          | val affDofFile = new File(workDir, tgtId + "," + srcId + "-aff${reg.affSuf}")
          | val phiDofFile = new File(workDir, tgtId + "," + srcId + "-phi${reg.phiSuf}")
          | val outDofFile = new File(workDir, tgtId + "," + srcId + "-phi${reg.dofSuf}")
          | val outLogFile = new File(workDir, tgtId + "," + srcId + "$logSuf")
          |
          | val log = new TaskLogger(outLogFile)
          |
          | val regCfg = new File(workDir, "reg.cfg")
          | val config = interpolate(\"\"\"${reg.config getOrElse ""}\"\"\".stripMargin.trim, parVal)
          | if (config.length > 0) {
          |   val fw = new FileWriter(regCfg)
          |   fw.write(config + "\\n")
          |   fw.close()
          |   val label = "Configuration"
          |   log.out(("-" * (40 - label.length / 2)) + label + ("-" * (40 - (label.length + 1) / 2)))
          |   log.out(config + "\\n" + ("-" * 80) + "\\n")
          | }
          |
          | // Convert input affine transformation from IRTK to required file format
          | if (dof2affCmd.isEmpty) {
          |   if (affDofFile != iniDofFile) Files.move(iniDofFile.toPath, affDofFile.toPath)
          | } else {
          |   val args = Map(
          |     "regId" -> regId,
          |     "in"    -> iniDofFile.toString,
          |     "dof"   -> iniDofFile.toString,
          |     "dofin" -> iniDofFile.toString,
          |     "aff"   -> affDofFile.toString,
          |     "out"   -> affDofFile.toString
          |   )
          |   val cmd = Cmd(dof2affCmd, args)
          |   log.out("\\nREPEAT> " + Cmd.toString(cmd) + "\\n")
          |   if (cmd.run(log).exitValue != 0) {
          |     throw new Exception("Affine transformation conversion command returned non-zero exit code: " + Cmd.toString(cmd))
          |   }
          | }
          |
          | // Run registration command
          | val args = parVal ++ Map(
          |   "regId"  -> regId,
          |   "parId"  -> parId,
          |   "target" -> tgtImgFile.toString,
          |   "source" -> srcImgFile.toString,
          |   "aff"    -> affDofFile.toString,
          |   "phi"    -> phiDofFile.toString,
          |   "config" -> regCfg.toString
          | )
          | val cmd = Cmd("/usr/bin/time", "-p") ++ Cmd(registerCmd, args)
          | log.out("\\nREPEAT> " + Cmd.toString(cmd) + "\\n")
          | log.resetTime()
          | if (cmd.run(log).exitValue != 0) {
          |   throw new Exception("Registration returned non-zero exit code: " + Cmd.toString(cmd))
          | }
          | val runTime = if (log.hasTime) log.time else Array[Double]()
          |
          | // Convert output transformation to IRTK format (optional)
          | if (phi2dofCmd.isEmpty) {
          |   if (outDofFile != phiDofFile) Files.move(phiDofFile.toPath, outDofFile.toPath)
          | } else {
          |   val args = Map(
          |     "regId"  -> regId,
          |     "parId"  -> parId,
          |     "dof"    -> outDofFile.toString,
          |     "out"    -> outDofFile.toString,
          |     "dofout" -> outDofFile.toString,
          |     "in"     -> phiDofFile.toString,
          |     "phi"    -> phiDofFile.toString
          |   )
          |   val cmd = Cmd(phi2dofCmd, args)
          |   log.out("\\nREPEAT> " + Cmd.toString(cmd) + "\\n")
          |   if (cmd.run(log).exitValue != 0) {
          |     throw new Exception("Failed to convert output transformation: " + Cmd.toString(cmd))
          |   }
          | }
          |
          | log.close()
        """.stripMargin
      ) set (
        name        := s"PairwiseRegistration(${reg.id}).registerImages",
        imports     += ("java.io.{File, FileWriter}", "java.nio.file.{Paths, Files}", "scala.sys.process._"),
        imports     += "com.andreasschuh.repeat.core._",
        usedClasses += (Config.getClass, classOf[TaskLogger], Cmd.getClass),
        inputs      += (regId, parId, parVal, tgtId, srcId, tgtImgPath, srcImgPath, iniDofPath, outDofPath, outLogPath),
        inputFiles  += (tgtImgFile, "${tgtId}" + imgExt, link = Workspace.shared),
        inputFiles  += (srcImgFile, "${srcId}" + imgExt, link = Workspace.shared),
        inputFiles  += (iniDofFile, "${tgtId},${srcId}-aff" + dofSuf, link = Workspace.shared),
        outputs     += (regId, parId, tgtId, srcId, outDofPath, outDofFile, outLogPath, outLogFile, runTime),
        dof2affCmd  := reg.dof2affCmd getOrElse Cmd(),
        registerCmd := reg.runCmd,
        phi2dofCmd  := reg.phi2dofCmd getOrElse Cmd()
      )
    ) source (
      FileSource("${tgtImgPath}", tgtImgFile),
      FileSource("${srcImgPath}", srcImgFile),
      FileSource("${iniDofPath}", iniDofFile)
    )
  }

  /** Capsule executed at the end of each registration */
  val done =
    Capsule(
      EmptyTask() set (
        name    := s"PairwiseRegistration(${reg.id}).done",
        inputs  += (regId, parId, tgtId, srcId, outDofPath, outDofFile, runTime),
        outputs += (regId, parId, tgtId, srcId, outDofPath, outDofFile, runTime)
      )
    )

  /** Capsule executed once the registration of a single image pair is finished */
  val regDoneOrSkipped = {
    Capsule(
      EmptyTask() set (
        name    := s"PairwiseRegistration(${reg.id}).doneOrSkipped",
        inputs  += (regId, parId, tgtId, srcId, outDofPath, runTime),
        outputs += (regId, parId, tgtId, srcId, outDofPath, runTime)
      )
    )
  }

  /** Capsule executed once all image pairs were registered with one set of registration parameters */
  val allDoneOrSkipped = {
    Capsule(
      ScalaTask(
        """
          | val regId = input.regId.head
          | val parId = input.parId.head
          | // TODO
        """.stripMargin
      ) set (
        name    := s"PairwiseRegistration(${reg.id}).forEachPairEnd",
        inputs  += (regId.toArray, parId.toArray, runTime.toArray),
        outputs += (regId, parId, runTime.toArray)
      )
    )
  }

  /** Capsule executed at the end of this workflow puzzle */
  val end =
    Capsule(
      EmptyTask() set (
        name    := s"PairwiseRegistration(${reg.id}).end",
        inputs  += regId.toArray,
        outputs += go,
        go      := true
      )
    )

  /**
   * Get workflow puzzle
   *
   * @param begin   End capsule of parent workflow puzzle (if any). Must output a Boolean variable named "go",
   *                which is consumed by the first task of this workflow puzzle.
   * @param needDof Condition indicating for each pair of images whether the registration result is required
   *                for the consecutive evaluation. By default, the registration is only executed when \c saveDofs
   *                is \c true and the transformation file in the workspace is outdated. Otherwise, when the
   *                transformation is not to be saved, whether or not a specific transformation is needed depends
   *                on whether or not a subsequent evaluation of the registration result has to be re-done. If all
   *                final evaluation results are still up-to-date, no registration needs to be executed.
   * @param message Status message printed for each pairwise registration to be performed.
   *
   * @return Workflow puzzle which registers all pairs of images with the different registration parameter settings.
   */
  def apply(begin: Option[Capsule] = None,
            needDof: Condition = Condition.True,
            message: String = "Registration for {regId=$regId, parId=$parId, tgtId=$tgtId, srcId=$srcId}") = {

    // Condition when to execute a pairwise registration
    def outdatedDof =
      if (saveTimes)
        Condition("outDofPath.toFile.lastModified < tgtImgPath.toFile.lastModified") ||
        Condition("outDofPath.toFile.lastModified < srcImgPath.toFile.lastModified") ||
        Condition("outDofPath.toFile.lastModified < affDofPath.toFile.lastModified")
      else
        Condition.False
    def missingTime = if (saveTimes) Condition("runTime.isEmpty") else Condition.False
    def missingDof = Condition("!outDofPath.toFile.exists") && needDof
    val condition = missingDof || outdatedDof || missingTime

    // Hooks to save output files to workspace
    def saveDof = if (saveDofs) List[Hook](CopyFileHook(outDofFile, "${outDofPath}")) else List[Hook]()
    def saveLog = if (saveLogs) List[Hook](CopyFileHook(outLogFile, "${outLogPath}")) else List[Hook]()
    val saveRes = saveDof ::: saveLog

    // Run pairwise registrations
    def runReg =
      begin.getOrElse(tasks.start) --
        (tasks.putRegId when "go") -- tasks.deleteTable(avgTimeTable, enabled = saveTimes && !Workspace.append) --
        forEachPar -< tasks.putParId -- tasks.backupTable(runTimeTable, enabled = saveTimes) -- forEachPair -<
        tasks.readFromTable(runTimeTable, Registration.times, runTime, enabled = saveTimes) -- regSwitchBegin --
          Switch(
            Case( condition, Display.QSUB(message) -- (registerImages on reg.runEnv hook (saveRes: _*)) -- Display.DONE(message) -- done),
            Case(!condition, Display.SKIP(message))
          ) -- regDoneOrSkipped >-
        allDoneOrSkipped >-
      end

    // Write runtime measurements
    def saveTime =
      regDoneOrSkipped -- Switch(
        Case( "runTime.nonEmpty", tasks.saveToTable(runTimeTable, Registration.times, runTime)),
        Case(s"runTime.isEmpty && $saveTimes", Display.WARN("Missing runtime for " + tasks.regParTgtAndSrcInfo))
      ) >- tasks.getHead("saveTime", regId, parId) -- tasks.finalizeTable(runTimeTable, enabled = saveTimes)

    // Write mean of runtime measurements
    def saveMeanTime =
      allDoneOrSkipped -- tasks.getMean(runTime, avgTime) -- Switch(
        Case( "avgTime.nonEmpty", tasks.saveToSummary(avgTimeTable, Registration.times, avgTime)),
        Case(s"avgTime.isEmpty && $saveTimes", Display.WARN("Invalid mean runtime for " + tasks.regAndParInfo))
      )

    // Return workflow puzzle
    runReg + saveTime + saveMeanTime
  }
}
