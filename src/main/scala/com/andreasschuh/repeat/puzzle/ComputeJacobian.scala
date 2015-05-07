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

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Compute Jacobian determinant map of output transformation for regularity assessment
 */
object ComputeJacobian {

  /**
   * Computes Jacobian determinant map of output transformation
   *
   * @param reg[in]        Registration info
   * @param parId[in]      Parameter set ID
   * @param tgtId[in,out]  ID of target image
   * @param srcId[in,out]  ID of source image
   * @param phiDof[in,out] Transformation from target to source
   * @param outJac[out]    Output Jacobian determinant map
   *
   * @return Puzzle piece to compute Jacobian determinant map
   */
  def apply(reg: Registration, parId: Prototype[Int],
            tgtId: Prototype[Int], tgtIm: Prototype[File], srcId: Prototype[Int],
            phiDof: Prototype[File], outJac: Prototype[File]) = {
    import Dataset.{imgPre, imgSuf}
    import Workspace.dofPre
    import FileUtil.join

    val outJacPath = join(reg.dofDir, dofPre + s"$${${tgtId.name}},$${${srcId.name}}" + reg.jacSuf).getAbsolutePath

    val begin = Capsule(EmptyTask() set (
        name    := s"${reg.id}-ComputeJacobianBegin",
        outputs += outJac
      ), strainer = true) source FileSource(outJacPath, outJac)

    val command = Val[Cmd]
    val run = Capsule(ScalaTask(
      s"""
        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
        | val ${outJac.name} = new java.io.File(workDir, "jac${reg.jacSuf}")
        | val args = Map(
        |   "target" -> ${tgtIm.name}.getPath,
        |   "phi"    -> ${phiDof.name}.getPath,
        |   "out"    -> ${outJac.name}.getPath
        | )
        | val cmd = Registration.command(${command.name}, args)
        | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
        | print(str)
        | FileUtil.mkdirs(${outJac.name})
        | val ret = cmd.!
        | if (ret != 0) throw new Exception("Command returned non-zero exit code!")
      """.stripMargin) set (
        name        := s"${reg.id}-ComputeJacobian",
        imports     += ("com.andreasschuh.repeat.core.{Config, FileUtil, Registration}", "scala.sys.process._"),
        usedClasses += (Config.getClass, FileUtil.getClass, Registration.getClass),
        inputs      += (tgtId, srcId, command),
        inputFiles  += (tgtIm, imgPre + "${tgtId}" + imgSuf, link = Workspace.shared),
        inputFiles  += (phiDof, dofPre + "${tgtId},${srcId}" + reg.phiSuf, link = Workspace.shared)
        outputs     += outJac,
        command     := reg.jacCmd
      ), strainer = true) hook CopyFileHook(outJac, outJacPath, move = Workspace.shared)

    begin -- Skip(run on Env.short, s"${outJac.name}.lastModified() > ${phiDof.name}.lastModified()")

    // TODO: Compute statistics of Jacobian determinant and store these in CSV file
  }
}
