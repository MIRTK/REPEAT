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
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Compose transformations from template to subject to get transformation between each pair of images
 */
object ComposeTemplateDofs {

  /**
   * @param tgtId[in,out] ID of target image
   * @param tgtIm[in,out] Path of target image
   * @param tgtDof[in]    Path of transformation from template to target
   * @param srcId[in,out] ID of source image
   * @param srcIm[in,out] Path of source image
   * @param srcDof[in]    Path of transformation from template to source
   * @param iniDof[out]   Composite transformation from target to source
   *
   * @return Puzzle piece to compute linear transformations from template image to input image
   */
  def apply(tgtId: Prototype[Int], tgtIm: Prototype[File], tgtDof: Prototype[File],
            srcId: Prototype[Int], srcIm: Prototype[File], srcDof: Prototype[File], iniDof: Prototype[File]) = {

    import Workspace.{dofIni, dofPre, dofSuf}
    import FileUtil.join

    val iniDofPath = join(dofIni, dofPre + s"$${${tgtId.name}},$${${srcId.name}}" + dofSuf).getAbsolutePath

    val begin = EmptyTask() set (
        name    := "ComposeTemplateDofsBegin",
        inputs  += (tgtId, tgtIm, tgtDof, srcId, srcIm, srcDof),
        outputs += (tgtId, tgtIm, tgtDof, srcId, srcIm, srcDof, iniDof)
      ) source FileSource(iniDofPath, iniDof)

    val compose = ScalaTask(
      s"""
        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
        | val ${iniDof.name} = new java.io.File(workDir, "initial$dofSuf")
        | IRTK.compose(${tgtDof.name}, ${srcDof.name}, ${iniDof.name}, true, false)
      """.stripMargin) set (
        name        := "ComposeTemplateDofs",
        imports     += "com.andreasschuh.repeat.core.{Config, FileUtil, IRTK}",
        usedClasses += (Config.getClass, FileUtil.getClass, IRTK.getClass),
        inputs      += (tgtId, tgtIm, srcId, srcIm),
        inputFiles  += (tgtDof, dofPre + "${tgtId}" + dofSuf, link = Workspace.shared),
        inputFiles  += (srcDof, dofPre + "${srcId}" + dofSuf, link = Workspace.shared),
        outputs     += (tgtId, tgtIm, srcId, srcIm, iniDof)
      ) hook CopyFileHook(iniDof, iniDofPath, move = Workspace.shared)

    val cond1 = s"${iniDof.name}.lastModified() > ${tgtDof.name}.lastModified()"
    val cond2 = s"${iniDof.name}.lastModified() > ${srcDof.name}.lastModified()"
    begin -- Skip(compose on Env.short by 25, cond1 + " && " + cond2)
  }
}
