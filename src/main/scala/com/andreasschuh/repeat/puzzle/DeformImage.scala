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

import java.nio.file.Path
import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file.CopyFileHook
import org.openmole.plugin.task.scala._
import org.openmole.plugin.source.file._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Apply output transformation to source image for qualitative assessment
 */
object DeformImage {

  /**
   * Applies output transformation to source image
   *
   * @param reg[in]           Registration info
   * @param regId[in,out]     ID of registration
   * @param parId[in,out]     ID of parameter set
   * @param tgtId[in,out]     ID of target image
   * @param srcId[in,out]     ID of source image
   * @param tgtImPath[in]     Path of target image
   * @param srcImPath[in]     Path of source image
   * @param outDof[in]    Transformation from target to source
   * @param outIm[in,out] Output image
   *
   * @return Puzzle piece to deform source image
   */
  def apply(reg: Registration, regId: Prototype[String], parId: Prototype[String], tgtId: Prototype[Int], srcId: Prototype[Int],
            tgtImPath: String, srcImPath: String, outDof: Prototype[Path], outIm: Prototype[Path], outImPath: String) = {

    val template = Val[Cmd]

    val begin =
      ScalaTask(
        s"""
          | val ${outIm.name} = Paths.get(s"$outImPath")
        """.stripMargin
      ) set (
        name    := s"${reg.id}-DeformImageBegin",
        imports += "java.nio.file.Paths",
        inputs  += (regId, parId, tgtId, srcId, outDof),
        outputs += (regId, parId, tgtId, srcId, outDof, outIm)
      )

    val task =
      ScalaTask(
        s"""
          | val args = Map(
          |   "target" -> s"$tgtImPath",
          |   "source" -> s"$srcImPath",
          |   "out"    -> ${outIm.name}.toString,
          |   "phi"    -> ${outDof.name}.toString
          | )
          | val cmd = command(template, args)
          | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
          | print(str)
          | val ret = cmd.!
          | if (ret != 0) throw new Exception("Command returned non-zero exit code!")
        """.stripMargin
      ) set (
        name        := s"${reg.id}-DeformImage",
        imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._"),
        usedClasses += Registration.getClass,
        inputs      += (regId, parId, tgtId, srcId, outIm, outDof, template),
        outputs     += (regId, parId, tgtId, srcId, outIm),
        template    := reg.deformImageCmd
      )

    begin -- Skip(task on Env.short, s"${outIm.name}.toFile.lastModified > ${outDof.name}.toFile.lastModified")
  }
}
