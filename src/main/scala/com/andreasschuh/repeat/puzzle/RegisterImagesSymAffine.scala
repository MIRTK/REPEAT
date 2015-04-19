// =====================================================================================================================
// Registration Performance Assessment Tool (REPEAT)
//
// Copyright (C) 2015  Andreas Schuh
//
//   This program is free software: you can redistribute it and/or modify
//   it under the terms of the GNU Affero General Public License as published by
//   the Free Software Foundation, either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU Affero General Public License for more details.
//
//   You should have received a copy of the GNU Affero General Public License
//   along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
// =====================================================================================================================

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


object RegisterImagesSymAffine {

  /**
   * @param tgtId[in,out] ID of target image
   * @param tgtIm[in,out] Path of target image
   * @param srcId[in,out] ID of source image
   * @param srcIm[in,out] Path of source image
   * @param iniDof[out]   Initial guess of transformation from target to source
   * @param outDof[out]   Output transformation from target to source
   * @param invDof[out]   Output transformation from source to target
   * @return Puzzle to compute linear transformations from template image to input image
   */
  def apply(tgtId:  Prototype[Int],  tgtIm:  Prototype[File], srcId:  Prototype[Int], srcIm: Prototype[File],
            iniDof: Prototype[File], outDof: Prototype[File], invDof: Prototype[File]) = {

    val configFile = GlobalSettings().configFile
    import Workspace.{dofAff, dofPre, dofSuf, logDir, logSuf}

    val regLog = Val[File]
    val outDofPath = FileUtil.join(dofAff, dofPre + "${tgtId},${srcId}" + dofSuf).getAbsolutePath
    val invDofPath = FileUtil.join(dofAff, dofPre + "${srcId},${tgtId}" + dofSuf).getAbsolutePath
    val regLogPath = FileUtil.join(logDir, "affine-reg", "${tgtId},${srcId}" + logSuf).getAbsolutePath

    val outDofSource = FileSource(outDofPath, outDof)
    val invDofSource = FileSource(invDofPath, invDof)
    val regLogSource = FileSource(regLogPath, regLog)

    val regBegin = EmptyTask() set (
        name    := "AffineRegImagesSymBegin",
        inputs  += (tgtId, tgtIm, srcId, srcIm, iniDof),
        outputs += (tgtId, tgtIm, srcId, srcIm, iniDof, outDof)
      ) source outDofSource

    val regTask = ScalaTask(
      s"""
        | GlobalSettings.setConfigDir(workDir)
        | IRTK.ireg(tgtIm, srcIm, Some(iniDof), outDof, Some(regLog),
        |   "Transformation model" -> "Affine",
        |   "No. of resolution levels" -> 2,
        |   "Padding value" -> 0
        | )
      """.stripMargin) set (
        name        := "AffineRegImagesSym",
        imports     += ("com.andreasschuh.repeat.core.GlobalSettings", "com.andreasschuh.repeat.core.IRTK"),
        usedClasses += (GlobalSettings.getClass, IRTK.getClass),
        inputs      += (tgtId, tgtIm, srcId, srcIm, iniDof),
        outputs     += (tgtId, tgtIm, srcId, srcIm, outDof, regLog),
        taskBuilder => configFile.foreach(taskBuilder.addResource(_))
      ) source (outDofSource, regLogSource)

    val invBegin = EmptyTask() set (
        name    := "InvertAffineDofBegin",
        inputs  += (tgtId, tgtIm, srcId, srcIm, outDof),
        outputs += (tgtId, tgtIm, srcId, srcIm, outDof, invDof)
      ) source invDofSource

    val invTask = ScalaTask(
      s"""
        | GlobalSettings.setConfigDir(workDir)
        | IRTK.invert(outDof, invDof)
      """.stripMargin) set (
        name        := "InvertAffineDof",
        imports     += ("com.andreasschuh.repeat.core.GlobalSettings", "com.andreasschuh.repeat.core.IRTK"),
        usedClasses += (GlobalSettings.getClass, IRTK.getClass),
        inputs      += (tgtId, tgtIm, srcId, srcIm, outDof),
        outputs     += (tgtId, tgtIm, srcId, srcIm, outDof, invDof),
        taskBuilder => configFile.foreach(taskBuilder.addResource(_))
      ) source invDofSource

    regBegin -- Skip(regTask on Env.short, "outDof.lastModified() >= iniDof.lastModified()") --
    invBegin -- Skip(invTask on Env.short, "invDof.lastModified() >= outDof.lastModified()")
  }
}