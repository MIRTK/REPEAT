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

import java.nio.file.{Path, Paths}
import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.plugin.hook.file.CopyFileHook
import org.openmole.plugin.task.scala._
import org.openmole.plugin.source.file._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Convert affine IRTK transformation to input transformation (optional)
 */
object ConvertDofToAff {

  /**
   * @param reg[in]            Registration info
   * @param regId[in,out]      ID of registration
   * @param tgtId[in,out]      ID of target image
   * @param srcId[in,out]      ID of source image
   * @param iniDofPath[in]     Path of affine transformation in IRTK format
   * @param affDofPath[in,out] Path of input transformation for registration command
   *
   * @return Puzzle which converts affine IRTK transformation to format needed by registration command
   */
  def apply(reg: Registration, regId: Prototype[String], tgtId: Prototype[Int], srcId: Prototype[Int],
            iniDofPath: Prototype[Path], affDofPath: Prototype[Path]) =
    Skip(
      reg.dof2affCmd match {
        case Some(dof2affCmd) =>
          val template = Val[Cmd]
          val task =
            ScalaTask(
              s"""
                | val args = Map(
                |   "regId" -> ${regId.name},
                |   "in"    -> ${iniDofPath.name}.toString,
                |   "dof"   -> ${iniDofPath.name}.toString,
                |   "dofin" -> ${iniDofPath.name}.toString,
                |   "aff"   -> ${affDofPath.name}.toString,
                |   "out"   -> ${affDofPath.name}.toString
                | )
                | val cmd = command(template, args)
                | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
                | print(str)
                | val ret = cmd.!
                | if (ret != 0) throw new Exception("Failed to convert affine transformation")
              """.stripMargin
            ) set(
              name        := s"${reg.id}-ConvertDofToAff",
              imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._"),
              usedClasses += Registration.getClass,
              inputs      += (regId, tgtId, srcId, affDofPath, iniDofPath),
              outputs     += (regId, tgtId, srcId, affDofPath),
              template    := dof2affCmd
            )
          Capsule(task) on Env.short
        case None =>
          val task =
            EmptyTask() set (
              name    := s"${reg.id}-UseDofAsAff",
              inputs  += (regId, tgtId, srcId, affDofPath, iniDofPath),
              outputs += (regId, tgtId, srcId, affDofPath)
            )
          Capsule(task) on Env.local
      },
      s"${affDofPath.name}.toFile().lastModified() > ${iniDofPath.name}.toFile().lastModified()"
    )
}
