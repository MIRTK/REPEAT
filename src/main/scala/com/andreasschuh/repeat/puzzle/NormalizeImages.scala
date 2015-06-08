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
import org.openmole.core.workflow.mole.Capsule
import org.openmole.core.workflow.data.Prototype
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern._

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Workflow puzzle for spatial normalization of input images
 */
object NormalizeImages {

  /** Get workflow puzzle for spatial normalization of input images */
  def apply() = new NormalizeImages()

}


/**
 * Spatially normalize input images
 *
 * This currently only means the computation of an affine template to image transformation which is used as
 * initial guess/input for the registration commands whose performance is to be evaluated. If it turns out
 * later that some of these require the input images to be resampled in a common image space, this may be done
 * by this workflow puzzle as well. In this case, however, the registration package under evaluation needs
 * to provide a tool for deforming at least a segmentation image by the composition of an affine, deformable,
 * and another affine transformation. Otherwise we would have to resample the segmentation images more than
 * once using nearest neighbor interpolation which introduces a significant sampling error in the evaluation.
 *
 * Another option would be to store the affine image to template transformation in the sform matrix of the
 * NIfTI-1 image file header. Not all registration packages will consider this transformation, however.
 *
 * @param start End capsule of parent workflow puzzle.
 */
class NormalizeImages(start: Option[Capsule] = None) extends Workflow(start) {

  /** Register image to template */
  protected def ireg(model: String, outDof: Prototype[File], iniDof: Option[Prototype[File]] = None) = {
    val outLog = Val[File]
    val refImg = Val[File]
    val srcImg = Val[File]
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
          | IRTK.ireg(refImg, srcImg, iniDof, ${outDof.name}, Some(outLog),
          |   "Transformation model" -> "${model.capitalize}",
          |   "Background value" -> dataSpace.refBkg.toString,
          |   "Strict step length range" -> "No",
          |   "Maximum streak of rejected steps" -> "4"
          | )
        """.stripMargin
      ) set (
        name        := wf + ".ireg-" + model,
        imports     += ("java.io.File", "com.andreasschuh.repeat.core.{Config, IRTK}"),
        usedClasses += (Config.getClass, IRTK.getClass),
        inputs      += (dataSpace, setId, refId, imgId),
        inputFiles  += (refImg, "${refId}" + Suffix.img, link = Workspace.shared),
        inputFiles  += (srcImg, "${imgId}" + Suffix.img, link = Workspace.shared),
        outputs     += (dataSpace, setId, refId, imgId, outDof, outLog)
      )
    if (iniDof != None) task.addInput(iniDof.get)
    Capsule(task) source (
      FileSource("${dataSpace.refImg(refId)}", refImg),
      FileSource("${dataSpace.padImg(imgId)}", srcImg)
    ) hook CopyFileHook(outLog, s"""$${dataSpace.logPath("ireg-$model", refId + "," + imgId + "${Suffix.log}")} """)
  }

  /** Puzzle corresponding to this workflow */
  def puzzle = _puzzle
  private lazy val _puzzle = {

    import Display._

    val regImg = {

      val dof = Val[File]

      val inputSet = "{setId=${setId}, refId=${refId}, imgId=${imgId}}"
      val rregMsg  = "Rigid alignment to template for " + inputSet
      val aregMsg  = "Affine registration to template for " + inputSet
      val skipMsg  = "Affine template transformation up-to-date for " + inputSet

      val cond =
        Condition(
          """
            | val refImg = dataSpace.refImg(refId).toFile
            | val srcImg = dataSpace.padImg(imgId).toFile
            | val affDof = dataSpace.affDof(refId, imgId).toFile
            | affDof.lastModified < refImg.lastModified || affDof.lastModified < srcImg.lastModified
          """.stripMargin
        )

      val save = CopyFileHook(dof, "${dataSpace.affDof(refId, imgId)}")

      Switch(
        Case(cond,
          QSUB(rregMsg, setId, refId, imgId) --
            (ireg("rigid", dof, None) on Env.short by 10) --
          DONE(rregMsg, setId, refId, imgId) --
          QSUB(aregMsg, setId, refId, imgId) --
            (ireg("affine", dof, Some(dof)) on Env.short by 10 hook save) --
          DONE(aregMsg, setId, refId, imgId)
        ),
        Case(!cond, SKIP(skipMsg, setId, refId, imgId))
      )
    }

    first -- forEachDataSet -< getDataSpace -- getRefId -- forEachImg -< regImg >- nop("forEachImgEnd") >- end
  }

}
