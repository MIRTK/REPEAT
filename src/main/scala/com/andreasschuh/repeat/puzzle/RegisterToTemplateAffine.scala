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
import org.openmole.plugin.hook.file._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core.{Environment => Env, _}


object RegisterToTemplateAffine {

  /**
   * @param refIm[in] Template image
   * @param srcId[in,out] ID of input image
   * @param srcIm[in,out] Input image
   * @param iniDof[out] Initial guess of transformation from template to input image
   * @param dof[out] Output transformation form template to input image
   * @return Puzzle to compute linear transformations from template image to input image
   */
  def apply(refIm:  Prototype[File], srcId: Prototype[Int], srcIm: Prototype[File],
            iniDof: Prototype[File], dof:   Prototype[File]) = {

    import Dataset.{refId, bgVal}
    import Workspace.{dofPre, dofSuf, dofAff, logDir, logSuf}

    val log = Val[File]
    val dofPath = FileUtil.join(dofAff,        dofPre + refId + s",$${${srcId.name}}" + dofSuf).getAbsolutePath
    val logPath = FileUtil.join(logDir, dofAff.getName, refId + s",$${${srcId.name}}" + logSuf).getAbsolutePath

    val dofSource = FileSource(dofPath, dof)

    val begin = EmptyTask() set(
        name    := "ComputeAffineTemplateDofsBegin",
        inputs  += (refIm, srcId, srcIm, iniDof),
        outputs += (refIm, srcId, srcIm, iniDof, dof)
      ) source dofSource

    val reg = ScalaTask(
      s"""
      | Config.dir(workDir)
      | val log = new java.io.File(workDir, "output$logSuf")
      | IRTK.ireg(${refIm.name}, ${srcIm.name}, Some(${iniDof.name}), ${dof.name}, Some(log),
      |   "Transformation model" -> "Affine",
      |   "Padding value" -> $bgVal
      | )
    """.stripMargin) set (
      name        := "ComputeAffineTemplateDofs",
      imports     += "com.andreasschuh.repeat.core.{Config, IRTK}",
      usedClasses += (Config.getClass, IRTK.getClass),
      inputs      += (refIm, srcId, srcIm, iniDof),
      outputs     += (refIm, srcId, srcIm, iniDof, dof),
      outputFiles += ("output" + logSuf, log),
      taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
    ) source dofSource hook CopyFileHook(log, logPath)

    begin -- Skip(reg on Env.short, s"${dof.name}.lastModified() > ${iniDof.name}.lastModified()")
  }
}
