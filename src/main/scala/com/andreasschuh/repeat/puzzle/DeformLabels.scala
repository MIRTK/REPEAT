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
 * Apply output transformation to propagate source labels for overlap assessment
 */
object DeformLabels {

  /**
   * Applies output transformation to propagate source labels
   *
   * @param reg[in]        Registration info
   * @param regId[in,out]  ID of registration
   * @param parId[in,out]  ID of parameter set
   * @param tgtId[in,out]  ID of target image
   * @param srcId[in,out]  ID of source image
   * @param tgtSegPath[in] Template path of target image segmentation
   * @param srcSegPath[in] Template path of target image segmentation
   * @param outDof[in]     Path of transformation from target to source
   * @param outSeg[in,out] Path of output segmentation image
   * @param outSegPath[in] Template path of output segmentation image
   *
   * @return Puzzle piece to propagate source labels
   */
  def apply(reg: Registration, regId: Prototype[String], parId: Prototype[String], tgtId: Prototype[Int], srcId: Prototype[Int],
            tgtSeg: Prototype[Path], tgtSegPath: String, srcSegPath: String, outDof: Prototype[Path],
            outSeg: Prototype[Path], outSegPath: String, modified: Prototype[Boolean]) = {

    val template = Val[Cmd]
    val srcSeg   = Val[Path]

    val begin =
      ScalaTask(
        s"""
          | val ${tgtSeg.name} = Paths.get(s"$tgtSegPath")
          | val ${srcSeg.name} = Paths.get(s"$srcSegPath")
          | val ${outSeg.name} = Paths.get(s"$outSegPath")
        """.stripMargin
      ) set (
        name     := s"${reg.id}-DeformLabelsBegin",
        imports  += "java.nio.file.Paths",
        inputs   += (regId, parId, tgtId, srcId, outDof),
        outputs  += (regId, parId, tgtId, srcId, outDof, tgtSeg, srcSeg, outSeg, modified),
        modified := false
      )

    val task =
      ScalaTask(
        s"""
          | val args = Map(
          |   "target" -> ${tgtSeg.name}.toString,
          |   "source" -> ${srcSeg.name}.toString,
          |   "out"    -> ${outSeg.name}.toString,
          |   "phi"    -> ${outDof.name}.toString
          | )
          | val cmd = command(template, args)
          | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
          | print(str)
          | val ret = cmd.!
          | if (ret != 0) throw new Exception("Label deformation command returned non-zero exit code!")
        """.stripMargin
      ) set (
        name        := s"${reg.id}-DeformLabels",
        imports     += ("com.andreasschuh.repeat.core.Registration.command", "scala.sys.process._"),
        usedClasses += Registration.getClass,
        inputs      += (regId, parId, tgtId, srcId, tgtSeg, srcSeg, outDof, outSeg, template),
        outputs     += (regId, parId, tgtId, srcId, tgtSeg, srcSeg, outDof, outSeg, modified),
        template    := reg.deformLabelsCmd,
        modified    := true
      )

    val info =
      DisplayHook(s"${Prefix.INFO}Transformed segmentation for {regId=$$regId, parId=$$parId, tgtId=$$tgtId, srcId=$$srcId}")

    val cond =
      s"""
        | ${outSeg.name}.toFile.lastModified > ${outDof.name}.toFile.lastModified &&
        | ${outSeg.name}.toFile.lastModified > ${srcSeg.name}.toFile.lastModified
      """.stripMargin

    begin -- Skip(task on Env.short hook info, cond)
  }
}
