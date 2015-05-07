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
import org.openmole.plugin.hook.file._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Affine pre-registration of image pairs
 */
object RegisterImagesSymAffine {

  /**
   * Performs affine registration between target and source
   *
   * At the moment, no real symmetric affine registration is performed. The registration is still biased towards
   * the chosen target image as only the transformation from target to source is computed. The inverse transformation
   * is found by simply inverting this transformation. The output transformations are hence inverse consistent.
   *
   * @todo Register also source to target and compute average of forward and backward transformation.
   *
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

    import Dataset.{imgPre, imgSuf, bgVal}
    import Workspace.{dofAff, dofPre, dofSuf, logDir, logSuf}
    import FileUtil.join

    val regLog = Val[File]

    val invDofPath = join(dofAff,        dofPre + s"$${${srcId.name}},$${${tgtId.name}}" + dofSuf).getAbsolutePath
    val outDofPath = join(dofAff,        dofPre + s"$${${tgtId.name}},$${${srcId.name}}" + dofSuf).getAbsolutePath
    val regLogPath = join(logDir, dofAff.getName, s"$${${tgtId.name}},$${${srcId.name}}" + logSuf).getAbsolutePath

    val regBegin = EmptyTask() set (
        name    := "AffineRegImagesSymBegin",
        inputs  += (tgtId, tgtIm, srcId, srcIm, iniDof),
        outputs += (tgtId, tgtIm, srcId, srcIm, iniDof, outDof)
      ) source FileSource(outDofPath, outDof)

    val reg = ScalaTask(
      s"""
        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
        | val ${outDof.name} = new java.io.File(workDir, "result$dofSuf")
        | val ${regLog.name} = new java.io.File(workDir, "output$logSuf")
        | IRTK.ireg(${tgtIm.name}, ${srcIm.name}, Some(${iniDof.name}), ${outDof.name}, Some(${regLog.name}),
        |   "Transformation model" -> "Affine",
        |   "No. of resolution levels" -> 2,
        |   "Padding value" -> $bgVal
        | )
      """.stripMargin) set (
        name        := "AffineRegImagesSym",
        imports     += "com.andreasschuh.repeat.core.{Config, IRTK}",
        usedClasses += (Config.getClass, IRTK.getClass),
        inputs      += (tgtId, srcId),
        inputFiles  += (tgtIm, imgPre + "${tgtId}" + imgSuf, link = Workspace.shared),
        inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, link = Workspace.shared),
        inputFiles  += (iniDof, dofPre + "${tgtId},${srcId}" + dofSuf, link = Workspace.shared),
        outputs     += (tgtId, tgtIm, srcId, srcIm, outDof, regLog)
      ) hook (
        CopyFileHook(outDof, outDofPath, move = Workspace.shared),
        CopyFileHook(regLog, regLogPath, move = Workspace.shared)
      )

    val invBegin = EmptyTask() set (
        name    := "InvertAffineDofBegin",
        inputs  += (tgtId, tgtIm, srcId, srcIm, outDof),
        outputs += (tgtId, tgtIm, srcId, srcIm, outDof, invDof)
      ) source FileSource(invDofPath, invDof)

    val inv = ScalaTask(
      s"""
        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
        | val ${invDof.name} = new java.io.File(workDir, "inverse$dofSuf")
        | IRTK.invert(${outDof.name}, ${invDof.name})
      """.stripMargin) set (
        name        := "InvertAffineDof",
        imports     += "com.andreasschuh.repeat.core.{Config, IRTK}",
        usedClasses += (Config.getClass, IRTK.getClass),
        inputs      += (tgtId, srcId),
        inputFiles  += (outDof, dofPre + "${tgtId},${srcId}" + dofSuf, link = Workspace.shared),
        outputs     += (tgtId, srcId, outDof, invDof)
      ) hook CopyFileHook(invDof, invDofPath, move = Workspace.shared)

    regBegin -- Skip(reg on Env.short by 10, s"${outDof.name}.lastModified() > ${iniDof.name}.lastModified()") --
    invBegin -- Skip(inv on Env.short by 25, s"${invDof.name}.lastModified() > ${outDof.name}.lastModified()")
  }
}
