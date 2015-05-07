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
   * @param runTime[out]   Runtime of registration command in seconds
   *
   * @return Puzzle piece to compute transformation from target to source
   */
  def apply(reg: Registration, parId: Prototype[Int], parVal: Prototype[Map[String, String]],
            tgtId: Prototype[Int], tgtIm: Prototype[File],
            srcId: Prototype[Int], srcIm: Prototype[File],
            affDof: Prototype[File], phiDof: Prototype[File],
            runTime: Prototype[Double]) = {
    import Dataset.{imgPre, imgSuf}
    import Workspace.{dofPre, logDir, logSuf}
    import FileUtil.join

    val regCmd = Val[Cmd]
    val regLog = Val[File]

    val phiDofPath = join(reg.dofDir, dofPre + s"$${${tgtId.name}},$${${srcId.name}}" + reg.phiSuf).getAbsolutePath
    val regLogPath = join(logDir, reg.id + "-${parId}", s"$${${tgtId.name}},$${${srcId.name}}" + logSuf).getAbsolutePath

    val begin = Capsule(EmptyTask() set (
        name    := s"${reg.id}-RegisterImagesBegin",
        outputs += phiDof
      ), strainer = true) source FileSource(phiDofPath, phiDof)

    val run = Capsule(ScalaTask(
      s"""
        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
        | val ${phiDof.name} = new java.io.File(workDir, "result${reg.phiSuf}")
        | val ${regLog.name} = new java.io.File(workDir, "output$logSuf")
        | val args = ${parVal.name} ++ Map(
        |   "regId"  -> "${reg.id}",
        |   "parId"  -> ${parId.name}.toString,
        |   "target" -> ${tgtIm.name}.getPath,
        |   "source" -> ${srcIm.name}.getPath,
        |   "aff"    -> ${affDof.name}.getPath,
        |   "phi"    -> ${phiDof.name}.getPath
        | )
        | val cmd = Registration.command(${regCmd.name}, args)
        | val log = new TaskLogger(${regLog.name})
        | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
        | if (!log.tee) print(str)
        | log.out(str)
        | FileUtil.mkdirs(${phiDof.name})
        | val startTime = System.nanoTime
        | val ret = cmd ! log
        | val runTime = (System.nanoTime - startTime) / 1e-9
        | if (ret != 0) throw new Exception("Registration returned non-zero exit code!")
      """.stripMargin) set (
        name        := s"${reg.id}-RegisterImages",
        imports     += ("com.andreasschuh.repeat.core.{Config, Registration, FileUtil, TaskLogger}", "scala.sys.process._"),
        usedClasses += (Config.getClass, Registration.getClass, FileUtil.getClass),
        inputs      += (tgtId, srcId, affDof, regCmd, parId, parVal),
        inputFiles  += (tgtIm, imgPre + "${tgtId}" + imgSuf, link = Workspace.shared),
        inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, link = Workspace.shared),
        inputFiles  += (affDof, dofPre + "${tgtId},${srcId}" + reg.affSuf, link = Workspace.shared),
        outputs     += (tgtId, tgtIm, srcId, srcIm, runTime),
        outputFiles += ("result" + reg.phiSuf, phiDof),
        outputFiles += ("output" + logSuf, regLog),
        regCmd      := reg.runCmd
      ), strainer = true) hook (
        CopyFileHook(phiDof, phiDofPath, move = Workspace.shared),
        CopyFileHook(regLog, regLogPath, move = Workspace.shared)
      )

    begin -- Skip(run on reg.runEnv, s"${phiDof.name}.lastModified() > ${affDof.name}.lastModified()")
  }
}
