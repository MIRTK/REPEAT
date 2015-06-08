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
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.hook.display._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern._

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Affine pre-registration of image pairs
 */
class RegisterImagesSymAffine(start: Option[Capsule] = None) extends Workflow(start) {

  /** Compose template to image transformations to get an initial guess of the target to source transformation */
  protected def compose(outDof: Prototype[File]) = {
    val tgtDof = Val[File]
    val srcDof = Val[File]
    val dofcombine = Val[String]
    val task =
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
        inputs      += (dataSpace, setId, tgtId, srcId),
        inputFiles  += (tgtDof, "${tgtId}" + Suffix.dof, link = Workspace.shared),
        inputFiles  += (srcDof, "${srcId}" + Suffix.dof, link = Workspace.shared),
        outputs     += (dataSpace, setId, tgtId, srcId, outDof),
        dofcombine  := IRTK.binPath("dofcombine")
      )
    Capsule(task) source (
      FileSource("${dataSpace.affDof(dataSpace.refId, tgtId)}", tgtDof),
      FileSource("${dataSpace.affDof(dataSpace.refId, srcId)}", srcDof)
    )
  }

  /** Register source to target image */
  protected def ireg(model: String, tgtId: Prototype[String], srcId: Prototype[String],
                     outDof: Prototype[File], iniDof: Option[Prototype[File]] = None) = {
    val tgtImg = Val[File]
    val srcImg = Val[File]
    val outLog = Val[File]
    val task =
      ScalaTask(
        s"""
          | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
          |
          | val ${outDof.name} = new File(workDir, "result${Suffix.dof}")
          | val outLog = new File(workDir, "output${Suffix.log}")
        """.stripMargin + (iniDof match {
          case Some(p) => s" val iniDof = Some(input.${p.name})"
          case None    =>  " val iniDof = None"
        }) +
        s"""
          | IRTK.ireg(tgtImg, srcImg, iniDof, ${outDof.name}, Some(outLog),
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
        inputs      += (dataSpace, setId, tgtId, srcId),
        inputFiles  += (tgtImg, s"$${${tgtId.name}}" + Suffix.img, link = Workspace.shared),
        inputFiles  += (srcImg, s"$${${srcId.name}}" + Suffix.img, link = Workspace.shared),
        outputs     += (dataSpace, setId, tgtId, srcId, outDof, outLog)
      )
    if (iniDof != None) task.addInput(iniDof.get)
    Capsule(task) source (
      FileSource(s"$${dataSpace.padImg(${tgtId.name})}", tgtImg),
      FileSource(s"$${dataSpace.padImg(${srcId.name})}", srcImg)
    ) hook CopyFileHook(outLog, s"""$${dataSpace.logPath("ireg-$model", ${tgtId.name} + "," + ${srcId.name} + "${Suffix.log}")} """)
  }

  /** Average target to source and inverse of source to target transformation */
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
        inputs      += (dataSpace, setId, tgtId, srcId),
        outputs     += (dataSpace, setId, tgtId, srcId, outDof),
        dofaverage  := IRTK.binPath("dofaverage")
      )
    dof.zipWithIndex.foreach {
      case (p, i) => task.addInputFile(p, (i+1).toString + Suffix.dof, link = Workspace.shared)
    }
    Capsule(task)
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
        inputs      += (dataSpace, setId, tgtId, srcId),
        inputFiles  += (outDof, "out" + Suffix.dof, link = Workspace.shared),
        outputs     += (dataSpace, setId, tgtId, srcId, outDof),
        dofinvert   := IRTK.binPath("dofinvert")
      )
    if (invDof != outDof) task.addOutput(invDof)
    Capsule(task)
  }

  /** Workflow puzzle */
  def puzzle = _puzzle
  private lazy val _puzzle = {

    import Display._

    val withImagePair =
      Capsule(
        EmptyTask() set (
          name    := wf + ".withImagePair",
          inputs  += (dataSpace, setId, tgtId, srcId),
          outputs += (dataSpace, setId, tgtId, srcId)
        )
      )

    val exploreImagePairs = first -- forEachDataSet -< getDataSpace -- forEachUniqueImgPair -< withImagePair
    val registerImagePair = {

      val iniDof = Val[File]
      val fwdDof = Val[File]
      val bwdDof = Val[File]
      val invDof = Val[File]
      val avgDof = Val[File]

      val infoVals = Seq(setId, tgtId, srcId)
      val fwdInfo  = "{setId=${setId}, tgtId=${tgtId}, srcId=${srcId}}"
      val bwdInfo  = "{setId=${setId}, tgtId=${srcId}, srcId=${tgtId}}"

      val iniMsg = "Making initial guess for "
      val regMsg = "Affine registering image pair for "
      val avgMsg = "Averaging forward and backward transformation for "

      // Compute target to source transformation
      val fwdRegEnd =
        Capsule(
          EmptyTask() set (
            name    := wf + ".fwdRegEnd",
            inputs  += (dataSpace, setId, tgtId, srcId, fwdDof),
            outputs += (dataSpace, setId, tgtId, srcId, fwdDof)
          )
        )

      val fwdReg =
          QSUB(iniMsg + fwdInfo, infoVals: _*) -- compose(iniDof) -- DONE(iniMsg + fwdInfo, infoVals: _*) --
          QSUB(regMsg + fwdInfo, infoVals: _*) -- ireg("affine", tgtId, srcId, fwdDof, Some(iniDof)) -- DONE(regMsg + fwdInfo, infoVals: _*) --
        fwdRegEnd

      // Compute source to target transformation
      val bwdRegEnd =
        Capsule(
          EmptyTask() set (
            name    := wf + ".bwdRegEnd",
            inputs  += invDof,
            outputs += invDof
          )
        )

      val bwdReg =
        fwdRegEnd --
          QSUB(iniMsg + bwdInfo, infoVals: _*) -- compose(iniDof) -- DONE(iniMsg + bwdInfo, infoVals: _*) --
          QSUB(regMsg + bwdInfo, infoVals: _*) -- ireg("affine", srcId, tgtId, bwdDof, Some(iniDof)) -- DONE(regMsg + bwdInfo, infoVals: _*) --
          QSUB(avgMsg + fwdInfo, infoVals: _*) -- dofinvert(bwdDof, invDof) --
        bwdRegEnd

      // Average transformations to get unbiased and symmetric transformations
      val avgDofs    = Slot(dofaverage(avgDof, fwdDof, invDof))
      val saveAvgDof = CopyFileHook(avgDof, "${dataSpace.affDof(tgtId, srcId)}")
      val saveInvDof = CopyFileHook(invDof, "${dataSpace.affDof(srcId, tgtId)}")

      val makeSym =
        (fwdRegEnd -- avgDofs) + (bwdRegEnd -- avgDofs) +
        (avgDofs hook saveAvgDof) -- (dofinvert(avgDof, invDof) hook saveInvDof) -- DONE(avgMsg + fwdInfo, infoVals: _*)

      // Perform symmetric affine registration only when needed
      val cond =
        Condition(
          """
            | val tgtImg = dataSpace.padImg(tgtId).toFile
            | val srcImg = dataSpace.padImg(srcId).toFile
            | val fwdDof = dataSpace.affDof(tgtId, srcId).toFile
            | val bwdDof = dataSpace.affDof(srcId, tgtId).toFile
            |
            | fwdDof.lastModified < tgtImg.lastModified || fwdDof.lastModified < srcImg.lastModified ||
            | bwdDof.lastModified < tgtImg.lastModified || bwdDof.lastModified < srcImg.lastModified
          """.stripMargin
        )

      withImagePair -- Switch(
        Case( cond, fwdReg + bwdReg + makeSym),
        Case(!cond, SKIP("Affine transformation up-to-date for " + fwdInfo, infoVals: _*))
      )
    }

    exploreImagePairs + (registerImagePair >- nop("forEachUniqueImgPairEnd") >- end)
  }
}
