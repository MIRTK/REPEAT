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
import org.openmole.core.workflow.mole.Capsule
import org.openmole.core.workflow.tools.Condition
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.hook.display._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern._

import com.andreasschuh.repeat.core.{Environment => Env, _}
import com.andreasschuh.repeat.puzzle.Display._


/**
 * Affine pre-registration of image pairs
 */
object RegisterImagesSymAffine {

  /** Get workflow puzzle for affine pre-registration of image pairs */
  def apply() = new RegisterImagesSymAffine()

  /**
   * Get workflow puzzle for affine pre-registration of image pairs
   * @param start End capsule of parent workflow puzzle.
   */
  def apply(start: Capsule) = new RegisterImagesSymAffine(Some(start))

}


/**
 * Affine pre-registration of image pairs
 *
 * @param start End capsule of parent workflow puzzle.
 */
class RegisterImagesSymAffine(start: Option[Capsule] = None) extends Workflow(start) {

  /** Compose template to image transformations to get an initial guess of the target to source transformation */
  protected def compose(tgtDof: Prototype[File], srcDof: Prototype[File], outDof: Prototype[File]) = {
    val dofcombine = Val[String]
    Capsule(
      ScalaTask(
        s"""
          | val ${outDof.name} = new File(workDir, "ini${Suffix.dof}")
          | val cmd = Cmd(dofcombine, ${srcDof.name}, ${tgtDof.name}, ${outDof.name}, "-invert2")
          | if (cmd.run().exitValue() != 0) {
          |   throw new Exception("Transformation composition command returned non-zero exit code: " + Cmd.toString(cmd))
          | }
        """.stripMargin
      ) set (
        name        := wf + ".compose",
        imports     += ("java.io.File", "scala.sys.process._", "com.andreasschuh.repeat.core._"),
        usedClasses += (Config.getClass, IRTK.getClass),
        inputs      += (setId, regId, tgtId, srcId),
        inputFiles  += (tgtDof, "${tgtId}" + Suffix.dof, link = WorkSpace.shared),
        inputFiles  += (srcDof, "${srcId}" + Suffix.dof, link = WorkSpace.shared),
        outputs     += (setId, regId, tgtId, srcId, outDof),
        dofcombine  := IRTK.binPath("dofcombine")
      ),
      strainer = true
    )
  }

  /** Register source to target image */
  protected def ireg(model: String,
                     tgtId: Prototype[String], tgtImg: Prototype[File],
                     srcId: Prototype[String], srcImg: Prototype[File],
                     outDof: Prototype[File], iniDof: Option[Prototype[File]] = None,
                     outLog: Option[Prototype[File]]) = {

    val outLogName = outLog match {
      case Some(p) => p.name
      case None => "outLog"
    }
    val task =
      ScalaTask(
        s"""
          | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
          |
          | val ${outDof.name} = new File(workDir, "result${Suffix.dof}")
          | val $outLogName = new File(workDir, "output${Suffix.log}")
        """.stripMargin + (iniDof match {
          case Some(p) => s" val iniDof = Some(input.${p.name})"
          case None    =>  " val iniDof = None"
        }) +
        s"""
          | IRTK.ireg(${tgtImg.name}, ${srcImg.name}, iniDof, ${outDof.name}, Some($outLogName),
          |   "Transformation model" -> "${model.capitalize}",
          |   "Background value" -> dataSpace.imgBkg.toString,
          |   "Strict step length range" -> "No",
          |   "Maximum streak of rejected steps" -> "4"
          | )
        """.stripMargin
      ) set (
        name        := wf + ".ireg-" + model,
        imports     += ("java.io.File", "com.andreasschuh.repeat.core.{Config, IRTK}"),
        usedClasses += (Config.getClass, IRTK.getClass),
        inputs      += (dataSpace, setId, regId, tgtId, srcId),
        inputFiles  += (tgtImg, s"$${${tgtId.name}}" + Suffix.img, link = WorkSpace.shared),
        inputFiles  += (srcImg, s"$${${srcId.name}}" + Suffix.img, link = WorkSpace.shared),
        outputs     += (setId, regId, tgtId, srcId, outDof)
      )
    if (iniDof != None) task.addInput(iniDof.get)
    if (outLog != None) task.addOutput(outLog.get)
    Capsule(task, strainer = true)
  }

  /** Average target to source with inverse of source to target transformation */
  protected def dofaverage(outDof: Prototype[File], dof: Prototype[File]*) = {
    val dofaverage = Val[String]
    val task =
      ScalaTask(
        s"""
          | val ${outDof.name} = new File(workDir, "avg${Suffix.dof}")
          | val average = Cmd(dofaverage, ${outDof.name}, ${dof.map(_.name).mkString(", ")}, "-all", "-max-frechet-iterations", "100")
          | if (average.run().exitValue() != 0) {
          |   throw new Exception("Transformation averaging command returned non-zero exit code: " + Cmd.toString(average))
          | }
        """.stripMargin
      ) set (
        name        := wf + ".dofaverage",
        imports     += ("java.io.File", "scala.sys.process._", "com.andreasschuh.repeat.core._"),
        usedClasses += Cmd.getClass,
        inputs      += (setId, regId, tgtId, srcId),
        outputs     += (setId, regId, tgtId, srcId, outDof),
        dofaverage  := IRTK.binPath("dofaverage")
      )
    dof.zipWithIndex.foreach {
      case (p, i) => task.addInputFile(p, (i+1).toString + Suffix.dof, link = WorkSpace.shared)
    }
    Capsule(task, strainer = true)
  }

  /** Invert affine transformation */
  protected def dofinvert(outDof: Prototype[File], invDof: Prototype[File]) = {
    val dofinvert = Val[String]
    val task =
      ScalaTask(
        s"""
          | val ${invDof.name} = new File(workDir, "inv${Suffix.dof}")
          | val invert = Cmd(dofinvert, ${outDof.name}, ${invDof.name})
          | if (invert.run().exitValue() != 0) {
          |   throw new Exception("Transformation inversion command returned non-zero exit code: " + Cmd.toString(invert))
          | }
        """.stripMargin
      ) set (
        name        := wf + ".dofinvert",
        imports     += ("java.io.File", "scala.sys.process._", "com.andreasschuh.repeat.core._"),
        usedClasses += Cmd.getClass,
        inputs      += (setId, regId, tgtId, srcId),
        inputFiles  += (outDof, "out" + Suffix.dof, link = WorkSpace.shared),
        outputs     += (setId, regId, tgtId, srcId, outDof),
        dofinvert   := IRTK.binPath("dofinvert")
      )
    if (invDof != outDof) task.addOutput(invDof)
    Capsule(task, strainer = true)
  }

  /** Merge multiple log files into a single file */
  def makeLog(outLog: Prototype[File], logs: Prototype[File]*) = {
    val appendLogsCode = logs.map { p =>
      s"""
         | Source.fromFile(${p.name}).getLines.foreach { line => out.write(line + "\\n") }
         | out.write("\\n")
       """.stripMargin
    }
    val task =
      ScalaTask(
        s"""
          | val ${outLog.name} = new File(workDir, "out${Suffix.log}")
          | val out = new FileWriter(${outLog.name})
          | ${appendLogsCode.mkString("\n")}
          | out.close()
        """.stripMargin
      ) set (
        name    := wf + s".makeLog(${outLog.name})",
        imports += ("java.io.{File, FileWriter}", "scala.io.Source"),
        outputs += outLog
      )
    logs.zipWithIndex.foreach {
      case (p, i) => task.addInputFile(p, (i+1) + Suffix.log)
    }
    Capsule(task, strainer = true)
  }

  /** (Pseudo-)Inverse-consistent registration task */
  // TODO: A true symmetric linear registration would be better because at each iteration symmetry is already enforced
  protected def fwdAndBwdReg(model: String,
                             tgtId:  Prototype[String], tgtImg: Prototype[File], tgtDof: Prototype[File],
                             srcId:  Prototype[String], srcImg: Prototype[File], srcDof: Prototype[File],
                             avgDof: Prototype[File],   invDof: Prototype[File], outLog: Prototype[File]) = {

    val iniDof = Val[File]
    val fwdDof = Val[File]
    val bwdDof = Val[File]
    val fwdLog = Val[File]
    val bwdLog = Val[File]

    val msgVals = Seq(setId, tgtId, srcId)
    val fwdInfo = s"{setId=$${setId}, tgtId=$${${tgtId.name}}, srcId=$${${srcId.name}}}"
    val bwdInfo = s"{setId=$${setId}, tgtId=$${${srcId.name}}, srcId=$${${tgtId.name}}}"

    val iniMsg = "Making initial guess for "
    val regMsg = "Affine registering image pair for "
    val avgMsg = "Averaging forward and backward transformation for "

    val first =
      Capsule(
        EmptyTask() set (
          name    := wf + ".symReg.first",
          inputs  += (dataSpace, setId, regId, tgtId, tgtImg, tgtDof, srcId, srcImg, srcDof),
          outputs += (dataSpace, setId, regId, tgtId, tgtImg, tgtDof, srcId, srcImg, srcDof)
        )
      )

    val last =
      Capsule(
        EmptyTask() set (
          name    := wf + ".symReg.last",
          inputs  += (dataSpace, setId, regId, tgtId, srcId, avgDof, invDof, outLog),
          outputs += (dataSpace, setId, regId, tgtId, srcId, avgDof, invDof, outLog)
        )
      )

    // Compute target to source transformation
    val fwdRegEnd =
      Capsule(
        EmptyTask() set (
          name    := wf + ".symReg.fwdRegEnd",
          inputs  += (dataSpace, setId, regId, tgtId, tgtImg, tgtDof, srcId, srcImg, srcDof, fwdDof, fwdLog),
          outputs += (dataSpace, setId, regId, tgtId, tgtImg, tgtDof, srcId, srcImg, srcDof, fwdDof, fwdLog)
        )
      )

    val fwdReg =
      first --
        QSUB(iniMsg + fwdInfo, msgVals: _*) --
          compose(tgtDof, srcDof, iniDof)   --
        DONE(iniMsg + fwdInfo, msgVals: _*) --
        QSUB(regMsg + fwdInfo, msgVals: _*) --
          ireg(model, tgtId, tgtImg, srcId, srcImg, fwdDof, Some(iniDof), Some(fwdLog)) --
        DONE(regMsg + fwdInfo, msgVals: _*) --
      fwdRegEnd

    // Compute source to target transformation
    val bwdRegEnd =
      Capsule(
        EmptyTask() set (
          name    := wf + ".symReg.bwdRegEnd",
          inputs  += (bwdDof, bwdLog),
          outputs += (bwdDof, bwdLog)
        )
      )

    val bwdReg =
      fwdRegEnd --
        QSUB(iniMsg + bwdInfo, msgVals: _*) --
          compose(srcDof, tgtDof, iniDof)   --
        DONE(iniMsg + bwdInfo, msgVals: _*) --
        QSUB(regMsg + bwdInfo, msgVals: _*) --
          ireg(model, srcId, srcImg, tgtId, tgtImg, bwdDof, Some(iniDof), Some(bwdLog)) --
        DONE(regMsg + bwdInfo, msgVals: _*) --
      bwdRegEnd

    // Average transformations to get unbiased and symmetric result
    val regResults =
      Slot(
        Capsule(
          EmptyTask() set (
            name    := wf + ".symReg.regResults",
            inputs  += (dataSpace, setId, regId, tgtId, srcId, fwdDof, fwdLog, bwdDof, bwdLog),
            outputs += (dataSpace, setId, regId, tgtId, srcId, fwdDof, fwdLog, bwdDof, bwdLog)
          )
        )
      )

    val finalize =
      (fwdRegEnd -- regResults) + (bwdRegEnd -- regResults) + (
        regResults --
        QSUB(avgMsg + fwdInfo, msgVals: _*) --
          dofinvert(bwdDof, invDof) -- dofaverage(avgDof, fwdDof, invDof) -- dofinvert(avgDof, invDof) --
        DONE(avgMsg + fwdInfo, msgVals: _*) --
        makeLog(outLog, fwdLog, bwdLog) -- last
      )

    // Inverse-consistent registration mole task
    MoleTask(fwdReg + bwdReg + finalize, last) set (name := wf + ".symReg")
  }

  /** Workflow puzzle */
  def puzzle = _puzzle
  private lazy val _puzzle = {

    val model = "affine"

    val tgtImg = Val[File]
    val srcImg = Val[File]
    val tgtDof = Val[File]
    val srcDof = Val[File]
    val avgDof = Val[File]
    val invDof = Val[File]
    val outLog = Val[File]

    val msgVals = Seq(setId, tgtId, srcId)
    val aregMsg = "Inverse-consistent registration for {setId=${setId}, tgtId=${tgtId}, srcId=${srcId}}"
    val skipMsg = "Affine transformation up-to-date for {setId=${setId}, tgtId=${tgtId}, srcId=${srcId}}"

    // Condition on when to run affine registration
    val cond =
      Condition(
        """
          | val tgtImg = dataSpace.padImg(tgtId).toFile
          | val srcImg = dataSpace.padImg(srcId).toFile
          | val fwdDof = dataSpace.affDof(regId, tgtId, srcId).toFile
          | val bwdDof = dataSpace.affDof(regId, srcId, tgtId).toFile
          |
          | fwdDof.lastModified < tgtImg.lastModified || fwdDof.lastModified < srcImg.lastModified ||
          | bwdDof.lastModified < tgtImg.lastModified || bwdDof.lastModified < srcImg.lastModified
        """.stripMargin
      )

    val reg =
      fwdAndBwdReg(model, tgtId, tgtImg, tgtDof, srcId, srcImg, srcDof, avgDof, invDof, outLog) source (
        FileSource("${dataSpace.affDof(regId, dataSpace.refId, tgtId)}", tgtDof),
        FileSource("${dataSpace.affDof(regId, dataSpace.refId, srcId)}", srcDof),
        FileSource("${dataSpace.padImg(tgtId)}", tgtImg),
        FileSource("${dataSpace.padImg(srcId)}", srcImg)
      ) hook (
        CopyFileHook(avgDof, "${dataSpace.affDof(regId, tgtId, srcId)}"),
        CopyFileHook(invDof, "${dataSpace.affDof(regId, srcId, tgtId)}"),
        CopyFileHook(outLog, s"""$${dataSpace.logPath(regId + "-$model", tgtId + "," + srcId + "${Suffix.log}")}""")
      )

    // Workflow puzzle
    begin -- forEachDataSet -< getDataSpace -- putRegId("ireg") -- forEachUniqueImgPair -<
      Switch(
        Case( cond, QSUB(aregMsg, msgVals: _*) -- (reg on Env.short) -- DONE(aregMsg, msgVals: _*)),
        Case(!cond, SKIP(skipMsg, msgVals: _*))
      ) >- nop("forEachUniqueImgPairEnd") >-
    end
  }
}
