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
import org.openmole.plugin.hook.display.DisplayHook
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Compute Jacobian determinant map of output transformation for regularity assessment
 */
object ComputeJacobian {

  /**
   * Computes Jacobian determinant map of output transformation
   *
   * @param reg[in]        Registration info
   * @param regId[in,out]  ID of registration
   * @param parId[in,out]  ID parameter set
   * @param tgtId[in,out]  ID of target image
   * @param srcId[in,out]  ID of source image
   * @param outDof[in]     Transformation from target to source
   * @param outJac[in,out] Path of output Jacobian determinant map
   * @param outJacPath[in] Template path of output jacobian determinant map
   *
   * @return Puzzle piece to compute Jacobian determinant map
   */
  def apply(reg: Registration, regId: Prototype[String], parId: Prototype[String], tgtId: Prototype[Int], srcId: Prototype[Int],
            tgtImPath: String, outDof: Prototype[Path], outJac: Prototype[Path], outJacPath: String) = {

    val template = Val[Cmd]

    val begin =
      ScalaTask(
        s"""
          | val ${outJac.name} = Paths.get(s"$outJacPath")
        """.stripMargin
      ) set (
        name := s"${reg.id}-ComputeJacobianBegin",
        imports += "java.nio.file.Paths",
        inputs  += (regId, parId, tgtId, srcId, outDof),
        outputs += (regId, parId, tgtId, srcId, outDof, outJac)
      )

    val task =
      ScalaTask(
        s"""
          | val args = Map(
          |   "target" -> s"$tgtImPath",
          |   "phi"    -> ${outDof.name}.toString,
          |   "out"    -> ${outJac.name}.toString
          | )
          | val cmd = command(template, args)
          | val outDir = ${outJac.name}.getParent
          | if (outDir != null) java.nio.file.Files.createDirectories(outDir)
          | val ret = cmd.!
          | if (ret != 0) {
          |   val str = cmd.mkString("\\"", "\\" \\"", "\\"\\n")
          |   throw new Exception("Jacobian command returned non-zero exit code: " + str)
          | }
        """.stripMargin
      ) set (
        name        := s"${reg.id}-ComputeJacobian",
        imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._"),
        usedClasses += Registration.getClass,
        inputs      += (regId, parId, tgtId, srcId, outJac, outDof, template),
        outputs     += (regId, parId, tgtId, srcId, outJac),
        template    := reg.jacCmd
      )

    val info =
      DisplayHook(Prefix.DONE + "Calculate Jacobian for {regId=${regId}, parId=${parId}, tgtId=${tgtId}, srcId=${srcId}}")

    val cond =
      s"${outJac.name}.toFile.lastModified > ${outDof.name}.toFile.lastModified"

    begin -- Skip(task on Env.short by 10 hook info, cond)
  }
}
