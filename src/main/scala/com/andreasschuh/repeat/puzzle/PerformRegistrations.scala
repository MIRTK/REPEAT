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

import org.openmole.core.dsl.Val
import org.openmole.core.workflow.builder._
import org.openmole.core.workflow.data._
import org.openmole.core.workflow.mole._
import org.openmole.core.workflow.puzzle._
import org.openmole.core.workflow.task._
import org.openmole.core.workflow.tools._
import org.openmole.core.workflow.transition._
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern._

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Workflow puzzle for pairwise registration of dataset images
 */
object PerformRegistrations {

  /** Get workflow puzzle for affine pre-registration of image pairs */
  def apply(reg: Registration) = new PerformRegistrations(reg)

  /**
   * Get workflow puzzle for affine pre-registration of image pairs
   * @param start End capsule of parent workflow puzzle.
   */
  def apply(reg: Registration, start: Capsule) = new PerformRegistrations(reg, Some(start))
}


/**
 * Register all pairs of dataset images with one another
 *
 * @param registration Registration method/implementation to use.
 * @param start        End capsule of parent workflow puzzle.
 * @param needDof      Condition indicating for each pair of images whether the registration result is required
 *                     for the consecutive evaluation. By default, the registration is only executed when
 *                     \c WorkSpace.keepDofs is \c true and the transformation file in the workspace is outdated.
 *                     Otherwise, when the transformation is not to be saved, whether or not a specific transformation
 *                     is needed depends on whether or not a subsequent evaluation of the registration result has to be
 *                     re-done. If all final evaluation results are still up-to-date, no registration needs to be executed.
 */
class PerformRegistrations(registration: Registration, start: Option[Capsule] = None, needDof: Option[Condition] = None) extends Workflow(start) {

  lazy val outDof  = Prototype[File]("outDof")
  lazy val outLog  = Prototype[File]("outLog")
  lazy val runTime = Prototype[Array[Double]]("runTime")

  lazy val regFinished      = Slot(nop("done"))
  lazy val regDoneOrSkipped = Slot(nop("regDoneOrSkipped"))
  lazy val allDoneOrSkipped = Slot(getHead("allDoneOrSkipped", setId, regId, parId))
  lazy val setDoneOrSkipped = Slot(getHead("setDoneOrSkipped", regId, parId))

  /** Register image pair using the registration command to be evaluated */
  protected def registerImages(workspace: Prototype[DataSpace], reg: Prototype[Registration], par: Prototype[Map[String, String]],
                               tgtId: Prototype[String], srcId: Prototype[String], outDof: Prototype[File],
                               outLog: Option[Prototype[File]] = None, runTime: Option[Prototype[Array[Double]]] = None) = {

    val tgtImg = Val[File]
    val srcImg = Val[File]
    val iniDof = Val[File]

    val task =
      ScalaTask(
        outLog.map(p => s""" val ${p.name} = new File(workDir, tgtId + \",\" + srcId + \"${Suffix.log}\")""").mkString +
        s"""
          | val params = Map("bgvalue" -> ${workspace.name}.imgBkg.toString) ++ input.${par.name}
          | val (${outDof.name}, _time) =
          |   reg(${tgtId.name}, tgtImg, ${srcId.name}, srcImg, parMap = Some(params),
          |       iniDof = Some(iniDof),  outLog = ${if (outLog == None) "None" else "Some(" + outLog.get.name + ")"},
          |       outDir = Some(workDir), tmpDir = Some(workDir))
        """.stripMargin +
        runTime.map(p => s""" val ${p.name} = _time""").mkString
      ) set (
        name       := wf + ".registerImages",
        imports    += "java.io.File",
        inputs     += (workspace, reg, par, tgtId, srcId),
        inputFiles += (tgtImg, "${tgtId}" + Suffix.img, link = WorkSpace.shared),
        inputFiles += (srcImg, "${srcId}" + Suffix.img, link = WorkSpace.shared),
        inputFiles += (iniDof, "${tgtId},${srcId}-aff" + Suffix.dof, link = WorkSpace.shared),
        outputs    += outDof
      )

    outLog .foreach(task.addOutput(_))
    runTime.foreach(task.addOutput(_))

    Capsule(task) source (
      FileSource(s"$${${workspace.name}.padImg(tgtId)}", tgtImg),
      FileSource(s"$${${workspace.name}.padImg(srcId)}", srcImg),
      FileSource(s"""$${${workspace.name}.affDof("ireg", tgtId, srcId)}""", iniDof)
    )
  }

  /** Workflow puzzle */
  def puzzle = _puzzle
  private lazy val _puzzle = {

    import Display._

    val saveTimes  = Time.modes.nonEmpty
    val runTimeCsv = ExpandedString(Time.resCsv)
    val avgTimeCsv = ExpandedString(Time.avgCsv)

    // Condition when to execute a pairwise registration
    def outdatedDof =
      if (saveTimes)
        Condition(
          """
            | val tgtImg = dataSpace.padImg(tgtId).toFile
            | val srcImg = dataSpace.padImg(srcId).toFile
            | val affDof = dataSpace.affDof("ireg", tgtId, srcId).toFile
            | val phiDof = dataSpace.phiDof(regId, parId, tgtId, srcId, phiPre = "", phiSuf = reg.phiSuf).toFile
            |
            | phiDof.lastModified < tgtImg.lastModified ||
            | phiDof.lastModified < srcImg.lastModified ||
            | phiDof.lastModified < affDof.lastModified
          """.stripMargin
        )
      else
        Condition.False
    def missingTime = if (saveTimes) Condition("runTime.isEmpty") else Condition.False
    def missingDof = needDof.getOrElse(Condition.True) &&
      Condition("""!dataSpace.phiDof(regId, parId, tgtId, srcId, phiPre = "", phiSuf = reg.phiSuf).toFile.exists""")
    val execCond = missingDof || outdatedDof || missingTime
    val skipCond = !execCond

    // Hooks to save output files to workspace
    def saveDof =
      if (WorkSpace.keepDofs)
        List[Hook](CopyFileHook(outDof, "${dataSpace.phiDof(regId, parId, tgtId, srcId)}"))
      else
        List[Hook]()

    def saveLog =
      if (WorkSpace.keepLogs)
        List[Hook](CopyFileHook(outLog, s"""$${dataSpace.logPath(regId + "-" + parId, tgtId + "," + srcId + "${Suffix.log}")}"""))
      else
        List[Hook]()

    val saveRes = saveDof ::: saveLog

    // Run pairwise registrations
    val msgVals = Seq(setId, regId, parId, tgtId, srcId)
    val execMsg = "Registration"
    val skipMsg = "Registration"

    def register =
      registerImages(dataSpace, reg, parVal, tgtId, srcId, outDof, Some(outLog), Some(runTime)) on registration.env hook (saveRes: _*)

    val execReg =
      begin -- forEachDataSet -< getDataSpace --
        deleteTable(avgTimeCsv, enabled = saveTimes && !WorkSpace.appCsv) --
        putReg(registration) -- forEachPar -< getParId --
          backupTable(runTimeCsv, enabled = saveTimes) -- forEachImgPair -<
            readFromTable(runTimeCsv, Time.modes, runTime, enabled = saveTimes) --
            Switch(
              Case(execCond, QSUB(execMsg, msgVals: _*) -- Strain(register) -- DONE(execMsg, msgVals: _*) -- regFinished),
              Case(skipCond, SKIP(skipMsg, msgVals: _*))
            ) -- regDoneOrSkipped >-
          allDoneOrSkipped >-
        setDoneOrSkipped >-
      end

    // Write runtime measurements
    lazy val saveTime =
      regDoneOrSkipped -- Switch(
        Case("runTime.nonEmpty", saveToTable(runTimeCsv, Time.modes, runTime)),
        Case("runTime.isEmpty", WARN("Missing runtime", setId, regId, parId, tgtId, srcId))
      ) >- getHead("saveTime", setId, regId, parId) -- finalizeTable(runTimeCsv, enabled = saveTimes)

    // Write mean of runtime measurements
    lazy val saveMeanTime = {
      val avgTime = Prototype[Array[Double]]("avgTime")
      allDoneOrSkipped -- getMean(runTime, avgTime) -- Switch(
        Case("avgTime.nonEmpty", saveToSummary(avgTimeCsv, Registration.times, avgTime)),
        Case("avgTime.isEmpty && " + saveTimes, WARN("Invalid mean runtime", setId, regId, parId))
      )
    }

    // Return workflow puzzle
    if (saveTimes) execReg + saveTime + saveMeanTime else execReg
  }
}
