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
 * Apply output transformation to source image for qualitative assessment
 */
object DeformImage {

  /**
   * Applies output transformation to source image
   *
   * @param reg[in]       Registration info
   * @param regId[in,out] ID of registration
   * @param parId[in,out] ID of parameter set
   * @param tgtId[in,out] ID of target image
   * @param srcId[in,out] ID of source image
   * @param tgtIm[out]    Path of target image
   * @param tgtImPath[in] Template path of target image
   * @param srcImPath[in] Template path of source image
   * @param outDof[in]    Transformation from target to source
   * @param outIm[in,out] Path of output image
   * @param outImPath[in] Template path of output image
   *
   * @return Puzzle piece to deform source image
   */
  def apply(reg: Registration, regId: Prototype[String], parId: Prototype[String], tgtId: Prototype[Int], srcId: Prototype[Int],
            tgtIm: Prototype[Path], tgtImPath: String, srcImPath: String, outDof: Prototype[Path], outIm: Prototype[Path], outImPath: String) = {

    val template = Val[Cmd]
    val srcIm    = Val[Path]

    val begin =
      ScalaTask(
        s"""
          | val ${outIm.name} = Paths.get(s"$outImPath")
          | val ${srcIm.name} = Paths.get(s"$srcImPath")
          | val ${tgtIm.name} = Paths.get(s"$tgtImPath")
        """.stripMargin
      ) set (
        name    := s"${reg.id}-DeformImageBegin",
        imports += "java.nio.file.Paths",
        inputs  += (regId, parId, tgtId, srcId, outDof),
        outputs += (regId, parId, tgtId, srcId, outDof, tgtIm, srcIm, outIm)
      )

    val task =
      ScalaTask(
        s"""
          | val args = Map(
          |   "target" -> ${tgtIm.name}.toString,
          |   "source" -> ${srcIm.name}.toString,
          |   "out"    -> ${outIm.name}.toString,
          |   "phi"    -> ${outDof.name}.toString
          | )
          | val cmd = command(template, args)
          | val outDir = ${outIm.name}.getParent
          | if (outDir != null) java.nio.file.Files.createDirectories(outDir)
          | val ret = cmd.!
          | if (ret != 0) {
          |   val str = cmd.mkString("\\"", "\\" \\"", "\\"\\n")
          |   throw new Exception("Image deformation command returned non-zero exit code: " + str)
          | }
        """.stripMargin
      ) set (
        name        := s"${reg.id}-DeformImage",
        imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._"),
        usedClasses += Registration.getClass,
        inputs      += (regId, parId, tgtId, srcId, tgtIm, srcIm, outIm, outDof, template),
        outputs     += (regId, parId, tgtId, srcId, tgtIm, outIm),
        template    := reg.deformImageCmd
      )

    val info =
      DisplayHook(Prefix.DONE + "Transform image for {regId=${regId}, parId=${parId}, tgtId=${tgtId}, srcId=${srcId}}")

    val cond =
      s"""
        | ${outIm.name}.toFile.lastModified > ${outDof.name}.toFile.lastModified &&
        | ${outIm.name}.toFile.lastModified > ${tgtIm.name }.toFile.lastModified &&
        | ${outIm.name}.toFile.lastModified > ${srcIm.name }.toFile.lastModified
      """.stripMargin

    begin -- Skip(task on Env.short by 10 hook info, cond)
  }
}
