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

import com.andreasschuh.repeat.core._

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Apply output transformation to source image for qualitative assessment
 */
object DeformImage {

  /**
   * Applies output transformation to source image
   *
   * @param reg[in]        Registration info
   * @param parId[in]      Parameter set ID
   * @param tgtId[in,out]  ID of target image
   * @param tgtIm[in,out]  Path of target image
   * @param srcId[in,out]  ID of source image
   * @param srcIm[in,out]  Path of source image
   * @param phiDof[in,out] Transformation from target to source
   * @param outIm[out]     Output image
   *
   * @return Puzzle piece to deform source image
   */
  def apply(reg: Registration, regId: Prototype[String], parId: Prototype[Int],
            tgtId: Prototype[Int], srcId: Prototype[Int],
            phiDof: Prototype[File], outIm: Prototype[File]) = {
    import Dataset.{imgPre, imgSuf}
    import Workspace.dofPre
    import FileUtil.join

    val tgtIm = Val[File]
    val srcIm = Val[File]

    val tgtImPath = join(Workspace.imgDir, imgPre + s"$${${tgtId.name}}" + imgSuf).getAbsolutePath
    val srcImPath = join(Workspace.imgDir, imgPre + s"$${${srcId.name}}" + imgSuf).getAbsolutePath
    val outImPath = join(reg.imgDir, imgPre + s"$${${srcId.name}}-$${${tgtId.name}}" + imgSuf).getAbsolutePath

    val begin = EmptyTask() set (
        name    := s"${reg.id}-DeformImageBegin",
        inputs  += (regId, parId, tgtId,        srcId,        phiDof),
        outputs += (regId, parId, tgtId, tgtIm, srcId, srcIm, phiDof, outIm)
      ) source (
        FileSource(tgtImPath, tgtIm),
        FileSource(srcImPath, srcIm),
        FileSource(outImPath, outIm)
      )

    val command = Val[Cmd]
    val run = ScalaTask(
      s"""
        | val ${outIm.name} = new java.io.File(workDir, "output$imgSuf")
        | val args = Map(
        |   "target" -> ${tgtIm.name}.getPath,
        |   "source" -> ${srcIm.name}.getPath,
        |   "out"    -> ${outIm.name}.getPath,
        |   "phi"    -> ${phiDof.name}.getPath
        | )
        | val cmd = Registration.command(${command.name}, args)
        | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
        | print(str)
        | val ret = cmd.!
        | if (ret != 0) throw new Exception("Command returned non-zero exit code!")
      """.stripMargin) set (
        name        := s"${reg.id}-DeformImage",
        imports     += ("com.andreasschuh.repeat.core.Registration", "scala.sys.process._"),
        usedClasses += Registration.getClass,
        inputs      += (regId, parId, tgtId, srcId, command),
        inputFiles  += (tgtIm, imgPre + "${tgtId}" + imgSuf, link = Workspace.shared),
        inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, link = Workspace.shared),
        inputFiles  += (phiDof, dofPre + "${tgtId},${srcId}" + reg.phiSuf, link = Workspace.shared),
        outputs     += (regId, parId, tgtId, srcId, outIm),
        command     := reg.deformImageCmd
      ) hook (
        CopyFileHook(outIm, outImPath, move = Workspace.shared)
      )

    begin -- Skip(run on Env.short, s"${outIm.name}.lastModified() > ${phiDof.name}.lastModified()")
  }
}
