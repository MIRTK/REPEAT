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

import com.andreasschuh.repeat.core.{Environment => Env, _}

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Skip


object RegisterToTemplateRigid {

  /**
   * @param refIm[in,out] Template image
   * @param srcId[in,out] ID of input image
   * @param srcIm[in,out] Input image
   * @param dof[out] Output transformation from template to input image
   * @return Puzzle to compute linear transformations from template image to input image
   */
  def apply(refIm: Prototype[File], srcId: Prototype[Int], srcIm: Prototype[File], dof: Prototype[File]) = {

    import Dataset.{refId, refSuf, imgPre, imgSuf, bgVal}
    import Workspace.{dofPre, dofSuf, dofRig, logDir, logSuf}
    import FileUtil.join

    val log = Val[File]

    val dofPath = join(dofRig,        dofPre + refId + s",$${${srcId.name}}" + dofSuf).getAbsolutePath
    val logPath = join(logDir, dofRig.getName, refId + s",$${${srcId.name}}" + logSuf).getAbsolutePath

    val begin = EmptyTask() set (
        name    := "ComputeRigidTemplateDofsBegin",
        inputs  += (refIm, srcId, srcIm),
        outputs += (refIm, srcId, srcIm, dof)
      ) source FileSource(dofPath, dof)

    val reg = ScalaTask(
      s"""
        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
        | val ${dof.name} = new java.io.File(workDir, "result$dofSuf")
        | val ${log.name} = new java.io.File(workDir, "output$logSuf")
        | IRTK.ireg(${refIm.name}, ${srcIm.name}, None, ${dof.name}, Some(${log.name}),
        |   "Transformation model" -> "Rigid",
        |   "Background value" -> $bgVal
        | )
      """.stripMargin) set (
        name        := "ComputeRigidTemplateDofs",
        imports     += "com.andreasschuh.repeat.core.{Config, IRTK}",
        usedClasses += (Config.getClass, IRTK.getClass),
        inputs      += srcId,
        inputFiles  += (refIm, refId + refSuf, link = Workspace.shared),
        inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, link = Workspace.shared),
        outputs     += (refIm, srcId, srcIm, dof, log)
      ) hook (
        CopyFileHook(dof, dofPath, move = Workspace.shared),
        CopyFileHook(log, logPath, move = Workspace.shared)
      )

    val cond1 = s"${dof.name}.lastModified() > ${refIm.name}.lastModified()"
    val cond2 = s"${dof.name}.lastModified() > ${srcIm.name}.lastModified()"
    begin -- Skip(reg on Env.short, cond1 + " && " + cond2)
  }
}
