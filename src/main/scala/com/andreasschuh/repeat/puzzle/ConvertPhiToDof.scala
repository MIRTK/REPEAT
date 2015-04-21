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
import org.openmole.plugin.task.scala._
import org.openmole.plugin.source.file._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core._


/**
 * Convert output transformation to IRTK format (optional)
 */
object ConvertPhiToDof {

  /**
   * @param reg    Registration info
   * @param phiDof Output transformation ("<phi>") of registration.
   *               If no phi2dof conversion is provided, tools for applying the transformation and computing
   *               evaluation measures such as the Jacobian determinant must be provided instead.
   *               These are then used instead for the assessment of the registration quality.
   * @param outDof Output transformation in IRTK format.
   *
   * @return Puzzle piece for conversion from IRTK format to format required by registration
   */
  def apply(reg: Registration, phiDof: Prototype[File], outDof: Prototype[File]) = {

    import Workspace.dofSuf

    val outDofPath   = FileUtil.join(reg.dofDir, s"$${${phiDof.name}.getName.dropRight(${reg.phiSuf.length}}$dofSuf").getAbsolutePath
    val outDofSource = FileSource(outDofPath, outDof)

    val begin = Capsule(EmptyTask() set (
        name    := s"${reg.id}-phi2dofBegin",
        outputs += outDof
      ), strainer = true) source outDofSource

    val phi2dof = reg.phi2dof match {
      case Some(command) =>
        val template = Val[Cmd]
        val task = ScalaTask(
          s"""
             | val args = Map(
             |   "phi"    -> ${phiDof.name}.getPath
             |   "dofout" -> ${outDof.name}.getPath,
             | )
             | val cmd = Registration.command(template, args)
             | val ret = cmd.!
             | if (ret != 0) throw new Exception("Failed to convert output transformation")
          """.stripMargin) set(
            name     := s"${reg.id}-phi2dof",
            imports  += ("com.andreasschuh.repeat.core.Registration", "scala.sys.process._"),
            inputs   += (phiDof, template),
            outputs  += outDof,
            template := command
          )
        Capsule(task, strainer = true) source outDofSource
      case None =>
        val task = ScalaTask(s"val ${outDof.name} = ${phiDof.name}") set (
            name    := s"${reg.id}-phi2dof",
            inputs  += phiDof,
            outputs += outDof
          )
        Capsule(task, strainer = true).toPuzzlePiece
    }

    begin -- Skip(phi2dof, s"${outDof.name}.lastModified() > ${phiDof.name}.lastModified()")
  }
}
