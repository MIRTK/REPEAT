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
import org.openmole.plugin.hook.file.CopyFileHook
import org.openmole.plugin.task.scala._
import org.openmole.plugin.source.file._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core._


/**
 * Convert affine IRTK transformation to input transformation (optional)
 */
object ConvertDofToAff {

  /**
   * @param reg[in]        Registration info
   * @param regId[in,out]  ID of registration
   * @param tgtId[in,out]  ID of target
   * @param srcId[in,out]  ID of source
   * @param iniDof[in]     Affine IRTK transformation
   * @param affDof[out]    Input transformation ("<aff>") for registration.
   *                       If no dof2aff conversion is provided, the IRTK transformation is used directly.
   *
   * @return Puzzle puzzle piece for conversion from IRTK format to format required by registration
   */
  def apply(reg: Registration, regId: Prototype[String], tgtId: Prototype[Int], srcId: Prototype[Int],
            iniDof: Prototype[File], affDof: Prototype[File]) = {

    import Workspace.{dofPre, dofSuf}

    val affDofPath = FileUtil.join(reg.affDir, dofPre + "${tgtId},${srcId}" + reg.affSuf).getAbsolutePath

    val begin = EmptyTask() set (
        name    := s"${reg.id}-ConvertDofToAffBegin",
        inputs  += (regId, tgtId, srcId, iniDof),
        outputs += (regId, tgtId, srcId, iniDof, affDof)
      ) source (
        FileSource(affDofPath, affDof)
      )

    val dof2aff = reg.dof2affCmd match {
      case Some(command) =>
        val template = Val[Cmd]
        val task = ScalaTask(
          s"""
            | val ${affDof.name} = new java.io.File(workDir, "aff${reg.affSuf}")
            | val args = Map(
            |   "regId" -> "${reg.id}",
            |   "in"    -> ${iniDof.name}.getPath,
            |   "dof"   -> ${iniDof.name}.getPath,
            |   "dofin" -> ${iniDof.name}.getPath,
            |   "aff"   -> ${affDof.name}.getPath,
            |   "out"   -> ${affDof.name}.getPath
            | )
            | val cmd = Registration.command(template, args)
            | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
            | print(str)
            | val ret = cmd.!
            | if (ret != 0) throw new Exception("Failed to convert affine transformation")
          """.stripMargin) set(
            name        := s"${reg.id}-ConvertDofToAff",
            imports     += ("com.andreasschuh.repeat.core.Registration", "scala.sys.process._"),
            usedClasses += Registration.getClass,
            inputs      += (regId, tgtId, srcId),
            inputFiles  += (iniDof, dofPre + "${tgtId},${srcId}" + dofSuf, link = Workspace.shared),
            outputs     += (regId, tgtId, srcId, affDof),
            template    := command
          )
        task hook CopyFileHook(affDof, affDofPath, move = Workspace.shared)
      case None =>
        val task = EmptyTask() set (
            name    := s"${reg.id}-UseDofAsAff",
            inputs  += (regId, tgtId, srcId, affDof),
            outputs += (regId, tgtId, srcId, affDof)
          )
        task.toCapsule.toPuzzlePiece
    }

    begin -- Skip(dof2aff, s"${affDof.name}.lastModified() > ${iniDof.name}.lastModified()")
  }
}
