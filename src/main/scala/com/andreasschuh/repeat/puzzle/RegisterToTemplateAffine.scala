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

//import java.io.File
//import scala.language.reflectiveCalls
//
//import org.openmole.core.dsl._
//import org.openmole.core.workflow.data.Prototype
//import org.openmole.plugin.grouping.batch._
//import org.openmole.plugin.hook.file._
//import org.openmole.plugin.source.file._
//import org.openmole.plugin.task.scala._
//import org.openmole.plugin.tool.pattern.Skip
//
//import com.andreasschuh.repeat.core.{Environment => Env, _}
//
//
//object RegisterToTemplateAffine {
//
//  /**
//   * @param refIm[in] Template image
//   * @param srcId[in,out] ID of input image
//   * @param srcIm[in,out] Input image
//   * @param iniDof[out] Initial guess of transformation from template to input image
//   * @param dof[out] Output transformation form template to input image
//   * @return Puzzle to compute linear transformations from template image to input image
//   */
//  def apply(refIm:  Prototype[File], srcId: Prototype[Int], srcIm: Prototype[File],
//            iniDof: Prototype[File], dof:   Prototype[File]) = {
//
//    import Dataset.{refId, refExt, imgPre, imgSuf, padVal}
//    import Workspace.{dofPre, dofSuf, dofAff, logDir, logSuf}
//    import FileUtil.join
//
//    val log = Val[File]
//
//    val dofPath = join(dofAff,        dofPre + refId + s",$${${srcId.name}}" + dofSuf).getAbsolutePath
//    val logPath = join(logDir, dofAff.getName, refId + s",$${${srcId.name}}" + logSuf).getAbsolutePath
//
//    val begin = EmptyTask() set(
//        name    := "ComputeAffineTemplateDofsBegin",
//        inputs  += (refIm, srcId, srcIm, iniDof),
//        outputs += (refIm, srcId, srcIm, iniDof, dof)
//      ) source FileSource(dofPath, dof)
//
//    val reg = ScalaTask(
//      s"""
//        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
//        | val ${dof.name} = new java.io.File(workDir, "result$dofSuf")
//        | val ${log.name} = new java.io.File(workDir, "output$logSuf")
//        | IRTK.ireg(${refIm.name}, ${srcIm.name}, Some(${iniDof.name}), ${dof.name}, Some(${log.name}),
//        |   "Transformation model" -> "Affine",
//        |   "Padding value" -> $padVal
//        | )
//      """.stripMargin) set (
//        name        := "ComputeAffineTemplateDofs",
//        imports     += "com.andreasschuh.repeat.core.{Config, IRTK}",
//        usedClasses += (Config.getClass, IRTK.getClass),
//        inputs      += srcId,
//        inputFiles  += (refIm, refId + refExt, link = Workspace.shared),
//        inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, link = Workspace.shared),
//        inputFiles  += (iniDof, dofPre + refId + ",${srcId}" + dofSuf, link = Workspace.shared),
//        outputs     += (refIm, srcId, srcIm, iniDof, dof, log)
//      ) hook (
//        CopyFileHook(dof, dofPath, move = Workspace.shared),
//        CopyFileHook(log, logPath, move = Workspace.shared)
//      )
//
//    begin -- Skip(reg on Env.short, s"${dof.name}.lastModified() > ${iniDof.name}.lastModified()")
//  }
//}
