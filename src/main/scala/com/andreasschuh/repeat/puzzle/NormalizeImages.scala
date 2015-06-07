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
//import java.nio.file.Path
//import scala.language.reflectiveCalls
//
//import org.openmole.core.dsl._
//import org.openmole.core.workflow.mole.{Capsule, Hook}
//import org.openmole.core.workflow.transition.Condition
//import org.openmole.plugin.grouping.batch._
//import org.openmole.plugin.hook.file._
//import org.openmole.plugin.sampling.combine._
//import org.openmole.plugin.sampling.csv._
//import org.openmole.plugin.source.file._
//import org.openmole.plugin.task.scala._
//import org.openmole.plugin.tool.pattern.{Switch, Case, Skip}
//
//import com.andreasschuh.repeat.core.{Environment => Env, _}
//
//
///**
// * Workflow puzzle for spatial normalization of input images
// */
//object NormalizeImages {
//
//  /** Get workflow puzzle for spatial normalization of input images */
//  def apply() = new NormalizeImages()
//}
//
///**
// * Spatially normalize input images
// *
// * This currently only means the computation of an affine template to image transformation which is used as
// * initial guess/input for the registration commands whose performance is to be evaluated. If it turns out
// * later that some of these require the input images to be resampled in a common image space, this may be done
// * by this workflow puzzle as well. In this case, however, the registration package under evaluation needs
// * to provide a tool for deforming at least a segmentation image by the composition of an affine, deformable,
// * and another affine transformation. Otherwise we would have to resample the segmentation images more than
// * once using nearest neighbor interpolation which introduces a significant sampling error in the evaluation.
// *
// * Another option would be to store the affine image to template transformation in the sform matrix of the
// * NIfTI-1 image file header. Not all registration packages will consider this transformation, however.
// */
//class NormalizeImages {
//
//  import Dataset.{refImg => _, _}
//  import Workspace._
//  import Variables._
//
//  val tgtImgPath = Val[Path]
//  val srcImgPath = Val[Path]
//
//  /** Explore all images */
//  private val forEachImg = {
//    val imgIdSampling = CSVSampling(imgCsv)
//    imgIdSampling.addColumn("Image ID", imgId)
//    Capsule(
//      ExplorationTask(imgIdSampling) set (
//        name   := "NormalizeImages.forEachImg",
//        inputs += go
//      )
//    )
//  }
//
//  /** Set paths of workflow puzzle input/output files */
//  private val initPaths =
//    Capsule(
//      ScalaTask(
//        s"""
//          | val tgtImgPath = Paths.get(s"$refImg")
//          | val srcImgPath = Paths.get(s"$padImg")
//        """.stripMargin
//      ) set (
//        name    := "NormalizeImages.initPaths",
//        imports += "java.nio.file.Paths",
//        inputs  += imgId,
//        outputs += (imgId, srcImgPath)
//      )
//    )
//
//  /** Capsule executed at the end of this workflow puzzle */
//  val end =
//    Capsule(
//      EmptyTask() set (
//        name    := s"PrepareImages.end",
//        inputs  += imgId.toArray,
//        outputs += go,
//        go      := true
//      )
//    )
//
//  /**
//   * Get workflow puzzle
//   *
//   * @param begin   End capsule of parent workflow puzzle (if any). Must output a Boolean variable named "go",
//   *                which is consumed by the first task of this workflow puzzle.
//   * @param message Status message printed for each image to be prepared.
//   *
//   * @return Workflow puzzle which prepares the input images for registration.
//   */
//  def apply(begin: Option[Capsule] = None, message: String = "Apply input mask for {imgId=$imgId}") = {
//
//    val applyMaskCond =
//      Condition("padImgPath.toFile.lastModified < orgMskPath.toFile.lastModified") ||
//      Condition("padImgPath.toFile.lastModified < orgMskPath.toFile.lastModified")
//
//    begin.getOrElse(Tasks.start) -- forEachImg -< initPaths --
//      copy(inImgPath, orgImgPath) -- copy(inMskPath, orgMskPath) -- copy(inSegPath, orgSegPath) -- Switch(
//        Case( applyMaskCond, Display.QSUB(message) -- applyMask -- Display.DONE(message)),
//        Case(!applyMaskCond, Display.SKIP(message))
//      ) >-
//    end
//  }
//}
