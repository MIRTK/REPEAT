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
import org.openmole.plugin.hook.file.CopyFileHook
import org.openmole.plugin.task.scala._
import org.openmole.plugin.source.file._
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
   * @param phiDof[in]     Transformation from target to source
   * @param outSeg[out]    Output segmentation image
   *
   * @return Puzzle piece to propagate source labels
   */
  def apply(reg: Registration, regId: Prototype[String], parId: Prototype[String],
            tgtId: Prototype[Int], srcId: Prototype[Int], phiDof: Prototype[File],
            outSeg: Prototype[File]) = {

    import Dataset.{segPre, segSuf}
    import Workspace.dofPre
    import FileUtil.join

    val tgtSeg = Val[File]
    val srcSeg = Val[File]

    val tgtSegPath = join(Workspace.segDir, segPre + s"$${${tgtId.name}}" + segSuf).getAbsolutePath
    val srcSegPath = join(Workspace.segDir, segPre + s"$${${srcId.name}}" + segSuf).getAbsolutePath
    val outSegPath = join(reg.segDir, segPre + s"$${${srcId.name}}-$${${tgtId.name}}" + segSuf).getAbsolutePath

    val begin = EmptyTask() set (
        name    := s"${reg.id}-DeformLabelsBegin",
        inputs  += (regId, parId, tgtId, srcId, phiDof),
        outputs += (regId, parId, tgtId, srcId, phiDof, outSeg)
      ) source FileSource(outSegPath, outSeg)

    val command = Val[Cmd]
    val run = ScalaTask(
      s"""
        | val ${outSeg.name} = new java.io.File(workDir, "output$segSuf")
        | val args = Map(
        |   "target" -> ${tgtSeg.name}.getPath,
        |   "source" -> ${srcSeg.name}.getPath,
        |   "out"    -> ${outSeg.name}.getPath,
        |   "phi"    -> ${phiDof.name}.getPath
        | )
        | val cmd = Registration.command(${command.name}, args)
        | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
        | print(str)
        | val ret = cmd.!
        | if (ret != 0) throw new Exception("Command returned non-zero exit code!")
      """.stripMargin) set (
        name        := s"${reg.id}-DeformLabels",
        imports     += ("com.andreasschuh.repeat.core.Registration", "scala.sys.process._"),
        usedClasses += Registration.getClass,
        inputs      += (regId, parId, tgtId, srcId, command),
        inputFiles  += (tgtSeg, segPre + "${tgtId}" + segSuf, link = Workspace.shared),
        inputFiles  += (srcSeg, segPre + "${srcId}" + segSuf, link = Workspace.shared),
        inputFiles  += (phiDof, dofPre + "${tgtId},${srcId}" + reg.phiSuf, link = Workspace.shared),
        outputs     += (regId, parId, tgtId, srcId, outSeg),
        command     := reg.deformLabelsCmd
      ) source (
        FileSource(tgtSegPath, tgtSeg),
        FileSource(srcSegPath, srcSeg)
      ) hook CopyFileHook(outSeg, outSegPath, move = Workspace.shared)

    begin -- Skip(run on Env.short, s"${outSeg.name}.lastModified() > ${phiDof.name}.lastModified()")
  }
}
