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

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Apply output transformation to propagate source labels for overlap assessment
 */
object DeformLabels {

  /**
   * Applies output transformation to propagate source labels
   *
   * @param reg[in]        Registration info
   * @param parId[in]      Parameter set ID
   * @param tgtId[in,out]  ID of target image
   * @param tgtSeg[in,out] Path of target segmentation image
   * @param srcId[in,out]  ID of source image
   * @param srcSeg[in,out] Path of source segmentation image
   * @param phiDof[in,out] Transformation from target to source
   * @param outSeg[out]    Output segmentation image
   *
   * @return Puzzle piece to propagate source labels
   */
  def apply(reg: Registration, parId: Prototype[Int],
            tgtId: Prototype[Int], tgtSeg: Prototype[File],
            srcId: Prototype[Int], srcSeg: Prototype[File],
            phiDof: Prototype[File], outSeg: Prototype[File]) = {
    import Dataset.{segPre, segSuf}
    import FileUtil.join

    val outSegPath   = join(reg.segDir, s"$${${parId.name}}", segPre + s"$${${srcId.name}}-$${${tgtId.name}}" + segSuf).getAbsolutePath
    val outSegSource = FileSource(outSegPath, outSeg)

    val begin = Capsule(EmptyTask() set (
        name    := s"${reg.id}-DeformLabelsBegin",
        outputs += outSeg
      ), strainer = true) source outSegSource

    val command = Val[Cmd]
    val run = Capsule(ScalaTask(
      s"""
        | Config.dir(workDir)
        | val args = Map(
        |   "target" -> ${tgtSeg.name}.getPath,
        |   "source" -> ${srcSeg.name}.getPath,
        |   "out"    -> ${outSeg.name}.getPath,
        |   "phi"    -> ${phiDof.name}.getPath
        | )
        | val cmd = Registration.command(${command.name}, args)
        | FileUtil.mkdirs(${outSeg.name})
        | val ret = cmd.!
        | if (ret != 0) throw new Exception("Command returned non-zero exit code!")
      """.stripMargin) set (
       name        := s"${reg.id}-DeformLabels",
       imports     += ("com.andreasschuh.repeat.core.{Config,FileUtil,Registration}", "scala.sys.process._"),
       usedClasses += Config.getClass,
       inputs      += (tgtSeg, srcSeg, phiDof, command),
       outputs     += (tgtSeg, srcSeg, phiDof, outSeg),
       command     := reg.deformLabelsCmd,
       taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
      ), strainer = true) source outSegSource

    begin -- Skip(run on Env.short, s"${outSeg.name}.lastModified() >= ${phiDof.name}.lastModified()")
  }
}
