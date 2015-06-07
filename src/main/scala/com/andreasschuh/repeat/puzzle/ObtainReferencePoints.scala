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

//import java.nio.file.Path
//import scala.language.reflectiveCalls
//
//import org.openmole.core.dsl._
//import org.openmole.core.workflow.mole.Capsule
//import org.openmole.core.workflow.transition.Condition
//import org.openmole.plugin.grouping.batch._
//import org.openmole.plugin.sampling.csv._
//import org.openmole.plugin.task.scala._
//import org.openmole.plugin.tool.pattern.{Switch, Case}
//
//import com.andreasschuh.repeat.core.{Environment => Env, IRTK, Dataset, Workspace}
//
//
///**
// * Workflow puzzle for generation of reference VTK point set to be transformed
// *
// * The point set is used in particular for the inverse-consistency and transitivity error evaluation.
// */
//object ObtainReferencePoints {
//  def apply() = new ObtainReferencePoints
//}
//
///**
// * Workflow puzzle for the conversion of reference image(s) to a VTK poly data file
// * which stores the world coordinates of the foreground voxel centers as its points.
// */
//class ObtainReferencePoints {
//
//  import Dataset.{imgCsv, imgPre, imgSuf, segPre, segSuf}
//  import Workspace.{imgDir, segDir, ptsDir, ptsSuf}
//
//  val go     = Val[Boolean]
//  val refId  = Val[String]
//  val refImg = Val[Path]
//  val refSeg = Val[Path]
//  val refPts = Val[Path]
//
//  private val forEachRef = {
//    val sampling = CSVSampling(imgCsv) set (columns += ("Image ID", refId))
//    Capsule(
//      ExplorationTask(sampling) set (
//        name   := "ReferencePointsPuzzle().forEachRef",
//        inputs += go
//      )
//    )
//  }
//
//  private val init =
//    Capsule(
//      ScalaTask(
//        s"""
//          | val refImg = Paths.get("$imgDir", "$imgPre" + refId + "$imgSuf")
//          | val refSeg = Paths.get("$segDir", "$segPre" + refId + "$segSuf")
//          | val refPts = Paths.get("$ptsDir", "voxel-centers", refId + "$ptsSuf")
//        """.stripMargin
//      ) set (
//        name    := "ReferencePointsPuzzle().begin",
//        imports += "java.nio.file.Paths",
//        inputs  += refId,
//        outputs += (refId, refImg, refSeg, refPts)
//      )
//    )
//
//  private val cond =
//    Condition("refPts.toFile.lastModified < refImg.toFile.lastModified") ||
//    Condition("refPts.toFile.lastModified < refSeg.toFile.lastModified")
//
//  private val task = {
//    val image2vtk = Val[String]
//    Capsule(
//      ScalaTask(
//        """
//          | val outDir = refPts.getParent
//          | if (outDir != null) Files.createDirectories(outDir)
//          | val exitCode = Cmd(image2vtk, refImg.toString, refPts.toString, "-mask", refSeg.toString, "-points").!
//          | if (exitCode != 0) throw new Exception("image2vtk returned non-zero exit code")
//        """.stripMargin
//      ) set(
//        name      := "ReferencePointsPuzzle().task",
//        imports   += ("java.nio.file.Files","scala.sys.process._", "com.andreasschuh.repeat.core._"),
//        inputs    += (refId, refImg, refSeg,refPts),
//        outputs   += (refId, refPts),
//        image2vtk := IRTK.binPath("image2vtk")
//      )
//    )
//  }
//
//  /** Capsule executed after the sample points of a specific reference image were written to file */
//  val done =
//    Capsule(
//      EmptyTask() set (
//        name    := "ReferencePointsPuzzle().end",
//        inputs  += (refId, refPts),
//        outputs += (refId, refPts, go),
//        go      := true
//      )
//    )
//
//  /** Capsule executed after all sample points have been written to file, i.e., the end of this workflow puzzle */
//  val end =
//    Capsule(
//      EmptyTask() set (
//        name    := "ReferencePointsPuzzle().done",
//        inputs  += refId.toArray,
//        outputs += go,
//        go      := true
//      )
//    )
//
//  /**
//   * Get workflow puzzle
//   *
//   * @param start End capsule of parent workflow puzzle (if any). Must output a Boolean variable named "go",
//   *              which is consumed by the first task of this workflow puzzle.
//   *
//   * @return Workflow puzzle which generates a VTK poly data file for each reference image
//   */
//  def apply(start: Option[Capsule] = None, desc: String = "Save voxel positions of $regId") = {
//    val begin = start getOrElse
//      Capsule(
//        EmptyTask() set (
//          outputs += go,
//          go      := true
//        )
//      )
//    begin --
//      forEachRef -< init -- Switch(
//        Case( cond, Display.QSUB(desc) -- (task on Env.short by 10) -- Display.DONE(desc)),
//        Case(!cond, Display.SKIP(desc))
//      ) -- done >-
//    end
//  }
//}
