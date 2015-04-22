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
import org.openmole.plugin.hook.file._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Run (deformable) pairwise registration
 */
object RegisterImages {

  /**
   * Performs (deformable) registration between target and source
   *
   * @param reg[in]        Registration info
   * @param parId[in,out]  ID of parameter set
   * @param parVal[in,out] Registration parameters
   * @param tgtId[in,out]  ID of target image
   * @param tgtIm[in,out]  Path of target image
   * @param srcId[in,out]  ID of source image
   * @param srcIm[in,out]  Path of source image
   * @param affDof[out]    Initial affine guess of transformation from target to source
   * @param phiDof[out]    Output transformation from target to source
   *
   * @return Puzzle piece to compute transformation from target to source
   */
  def apply(reg: Registration, parId: Prototype[Int], parVal: Prototype[Map[String, String]],
            tgtId: Prototype[Int], tgtIm: Prototype[File],
            srcId: Prototype[Int], srcIm: Prototype[File],
            affDof: Prototype[File], phiDof: Prototype[File]) = {
    import Workspace.{dofPre, logDir, logSuf}
    import FileUtil.join

    val regCmd = Val[Cmd]
    val regLog = Val[File]

    val phiDofPath = join(reg.dofDir, dofPre + s"$${${tgtId.name}},$${${srcId.name}}" + reg.phiSuf).getAbsolutePath
    val regLogPath = join(logDir, reg.id, "${parId}", s"$${${tgtId.name}},$${${srcId.name}}" + logSuf).getAbsolutePath

    val phiDofSource = FileSource(phiDofPath, phiDof)

    val begin = Capsule(EmptyTask() set (
        name    := s"${reg.id}-RegisterImagesBegin",
        outputs += phiDof
      ), strainer = true) source phiDofSource

    val run = Capsule(ScalaTask(
      s"""
        | Config.dir(workDir)
        | val args = ${parVal.name} ++ Map(
        |   "target" -> ${tgtIm.name}.getAbsolutePath,
        |   "source" -> ${srcIm.name}.getAbsolutePath,
        |   "aff"    -> ${affDof.name}.getAbsolutePath,
        |   "phi"    -> ${phiDof.name}.getAbsolutePath
        | )
        | val regLog = new java.io.File(workDir, "output$logSuf")
        | val cmd = Registration.command(${regCmd.name}, args)
        | val log = new TaskLogger(regLog)
        | val str = cmd.mkString("> \\"", "\\" \\"", "\\"\\n")
        | if (!log.tee) println(str)
        | log.out(str)
        | FileUtil.mkdirs(${phiDof.name})
        | val ret = cmd ! log
        | if (ret != 0) throw new Exception("Registration returned non-zero exit code!")
      """.stripMargin) set (
        name        := s"${reg.id}-RegisterImages",
        imports     += ("com.andreasschuh.repeat.core.{Config,Registration,FileUtil,TaskLogger}", "scala.sys.process._"),
        usedClasses += Config.getClass,
        inputs      += (tgtIm, srcIm, affDof, regCmd, parVal),
        outputs     += (tgtIm, srcIm, phiDof),
        outputFiles += ("output" + logSuf, regLog),
        regCmd      := reg.runCmd,
        taskBuilder => Config().file.foreach(taskBuilder.addResource(_))
      ), strainer = true) source phiDofSource hook CopyFileHook(regLog, regLogPath)

    begin -- Skip(run on Env.long, s"${phiDof.name}.lastModified() > ${affDof.name}.lastModified()")
  }
}