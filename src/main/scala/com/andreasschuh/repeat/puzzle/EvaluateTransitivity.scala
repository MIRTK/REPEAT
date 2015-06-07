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
//import org.openmole.core.workflow.mole.Capsule
//import org.openmole.core.workflow.transition.Condition
//import org.openmole.plugin.grouping.batch._
//import org.openmole.plugin.sampling.combine._
//import org.openmole.plugin.sampling.csv._
//import org.openmole.plugin.task.scala._
//import org.openmole.plugin.tool.pattern.{Switch, Case}
//
//import com.andreasschuh.repeat.core.{Environment => Env, _}
//
//
///**
// * Workflow puzzle for evaluation of transitivity error
// */
//object EvaluateTransitivity extends Configurable("evaluation.transitivity") {
//
//  /** Whether to evaluate transitivity error */
//  val enabled = getBooleanProperty("enabled")
//
//  /** Whether to report squared Euclidean distances */
//  val squared = getBooleanProperty("squared")
//
//  /** Output directory of transitivity error maps */
//  val outDir = FileUtil.normalize(FileUtil.join(Workspace.dir, getStringProperty(".workspace.output.te-maps")))
//
//  /** Get workflow puzzle for transitivity error evaluation of specified registration */
//  def apply(reg: Registration) = new EvaluateTransitivity(reg)
//}
//
///**
// * Evaluate voxel-wise transitivity error
// *
// * @param reg Registration to be evaluated.
// */
//class EvaluateTransitivity(reg: Registration) {
//
//  import Dataset.{imgCsv, imgPre, imgSuf, imgExt}
//  import Workspace.{imgDir, ptsDir, ptsSuf, dofPre}
//  import EvaluateTransitivity.{outDir, squared}
//
//  val go     = Val[Boolean]
//  val regId  = Val[String]
//  val parId  = Val[String]
//  val refId  = Val[String]
//  val im1Id  = Val[String]
//  val im2Id  = Val[String]
//  val refPts = Val[Path]
//  val a2bDof = Val[Path]
//  val b2cDof = Val[Path]
//  val c2aDof = Val[Path]
//  val ptsMap = Val[File]
//  val avgMap = Val[Path]
//
//  /** Explore all parameter settings of the registration */
//  private val forEachPar = {
//    val sampling = CSVSampling(reg.parCsv) set (columns += ("ID", parId))
//    Capsule(
//      ExplorationTask(sampling) set (
//        name    := s"EvaluateTransitivity(${reg.id}).forEachReg",
//        inputs  += go,
//        outputs += regId,
//        regId   := reg.id
//      )
//    )
//  }
//
//  /** Explore all reference images used for voxel-wise "cumulative" (i.e., mean) transitivity error evaluation */
//  // TODO: Only use template of dataset as reference (requires population template)
//  private val forEachRef = {
//    val sampling = CSVSampling(imgCsv) set (columns += ("Image ID", refId))
//    Capsule(
//      ExplorationTask(sampling) set (
//        name    := s"EvaluateTransitivity(${reg.id}).forEachRef",
//        inputs  += (regId, parId),
//        outputs += (regId, parId)
//      )
//    )
//  }
//
//  /** Set path of reference point set file */
//  private val initPaths =
//    Capsule(
//      ScalaTask(
//        s"""
//          | val refPts = Paths.get("$ptsDir", "voxel-centers", refId + "$ptsSuf")
//          | val avgMap = Paths.get(s"$outDir", refId + "$imgExt")
//        """.stripMargin
//      ) set (
//        name    := s"EvaluateTransitivity(${reg.id}).initPaths",
//        imports += "java.nio.file.Paths",
//        inputs  += (regId, parId, refId),
//        outputs += (regId, parId, refId, refPts, avgMap)
//      )
//    )
//
//  /** Explore all source images which were registered to the reference image */
//  private val forEachPair = {
//    val im1Sampling = CSVSampling(imgCsv) set (columns += ("Image ID", im1Id))
//    val im2Sampling = CSVSampling(imgCsv) set (columns += ("Image ID", im2Id))
//    Capsule(
//      ExplorationTask((im1Sampling x im2Sampling) filter "im1Id != im2Id") set (
//        name    := s"EvaluateTransitivity(${reg.id}).forEachImg",
//        inputs  += (regId, parId, refId, refPts),
//        outputs += (regId, parId, refId, refPts)
//      )
//    )
//  }
//
//  /** Transform reference points from A to B, B to C, and back to A again, then calculate difference to initial reference points */
//  private val evaluate = {
//    val deformPts = Val[Cmd]
//    val calcDiff  = Val[String]
//    Capsule(
//      ScalaTask(
//        s"""
//          | val a2bDof = Paths.get(s"${reg.dofDir}", "$dofPre" + refId + "," + im1Id + "${reg.dofSuf}")
//          | val b2cDof = Paths.get(s"${reg.dofDir}", "$dofPre" + im1Id + "," + im2Id + "${reg.dofSuf}")
//          | val c2aDof = Paths.get(s"${reg.dofDir}", "$dofPre" + im2Id + "," + refId + "${reg.dofSuf}")
//          | val outPts = new File(workDir, refId + "-" + im1Id + "-" + im2Id + "-pts$ptsSuf")
//          | val ptsMap = new File(workDir, refId + "-" + im1Id + "-" + im2Id + "-te$ptsSuf")
//          |
//          | // Transform reference points to image 1
//          | val args1 = Map(
//          |   "target" -> refPts.toString,
//          |   "in"     -> refPts.toString,
//          |   "out"    -> outPts.toString,
//          |   "phi"    -> a2bDof.toString
//          | )
//          | val cmd1 = command(deformPts, args1)
//          | if (0 != cmd1.!) {
//          |   val str = cmd1.mkString("\\"", "\\" \\"", "\\"\\n")
//          |   throw new Exception("Point set transformation command returned non-zero exit code: " + str)
//          | }
//          |
//          | // Transform points to image 2
//          | val args2 = Map(
//          |   "target" -> outPts.toString,
//          |   "in"     -> outPts.toString,
//          |   "out"    -> outPts.toString,
//          |   "phi"    -> b2cDof.toString
//          | )
//          | val cmd2 = command(deformPts, args2)
//          | if (0 != cmd2.!) {
//          |   val str = cmd2.mkString("\\"", "\\" \\"", "\\"\\n")
//          |   throw new Exception("Point set transformation command returned non-zero exit code: " + str)
//          | }
//          |
//          | // Transform points to back to reference
//          | val args3 = Map(
//          |   "target" -> outPts.toString,
//          |   "in"     -> outPts.toString,
//          |   "out"    -> outPts.toString,
//          |   "phi"    -> c2aDof.toString
//          | )
//          | val cmd3 = command(deformPts, args3)
//          | if (0 != cmd3.!) {
//          |   val str = cmd3.mkString("\\"", "\\" \\"", "\\"\\n")
//          |   throw new Exception("Point set transformation command returned non-zero exit code: " + str)
//          | }
//          |
//          | // Calculate squared Euclidean distance between transformed points and reference points
//          | val squaredOpt = if ($squared) Cmd("-squared") else Cmd()
//          | val cmd4 = Cmd(calcDiff, refPts.toString, outPts.toString, "-output", ptsMap.toString) ++ squaredOpt
//          | if (0 != cmd4.!) {
//          |   val str = cmd4.mkString("\\"", "\\" \\"", "\\"\\n")
//          |   throw new Exception("Point set registration evaluation command returned non-zero exit code: " + str)
//          | }
//        """.stripMargin
//      ) set (
//        name        := s"",
//        imports     += ("java.io.File", "java.nio.file.{Paths, Files}", "scala.sys.process._", "com.andreasschuh.repeat.core._"),
//        usedClasses += IRTK.getClass,
//        inputs      += (regId, parId, refId, refPts, im1Id, im2Id),
//        outputs     += (regId, parId, refId, refPts, ptsMap),
//        deformPts   := reg.deformPointsCmd,
//        calcDiff    := IRTK.binPath("pevaluation")
//      )
//    )
//  }
//
//  /** Average errors computed w.r.t. a given reference point set and convert point set to an error map/image */
//  private val average = {
//    val calculate = Val[String]
//    val vtk2image = Val[String]
//    Capsule(
//      ScalaTask(
//        s"""
//          | val regId  = input.regId.head
//          | val parId  = input.parId.head
//          | val refId  = input.refId.head
//          | val refImg = Paths.get("$imgDir", "$imgPre" + refId + "$imgSuf")
//          | val accMap = new File(workDir, "acc$ptsSuf")
//          | val avgMap = Paths.get(s"$outDir", refId + "$imgExt")
//          |
//          | // Create output directories
//          | val outDir = avgMap.getParent
//          | if (outDir != null) Files.createDirectories(outDir)
//          |
//          | // Sum individual transitivity errors
//          | ptsMap.foreach(curMap => {
//          |   if (accMap.exists) {
//          |     val cmd1 = Cmd(calculate, accMap.toString, "+", curMap.toString, "=", accMap.toString)
//          |     if (0 != cmd1.!) {
//          |       val str = cmd1.mkString("\\"", "\\" \\"", "\\"\\n")
//          |       throw new Exception("transitivity error summation tool return non-zero exit code: " + str)
//          |     }
//          |   } else {
//          |     Files.copy(curMap.toPath, accMap.toPath)
//          |   }
//          | })
//          |
//          | // Divide by number of transformation pairs
//          | val cmd2 = Cmd(calculate, accMap.toString, "/", ptsMap.size.toString, "=", accMap.toString)
//          | if (0 != cmd2.!) {
//          |   val str = cmd2.mkString("\\"", "\\" \\"", "\\"\\n")
//          |   throw new Exception("transitivity error division tool returned non-zero exit code: " + str)
//          | }
//          |
//          | // Convert point set to average error map (i.e., image)
//          | val cmd3 = Cmd(vtk2image, accMap.toString, avgMap.toString, "-template", refImg.toString, "-nn", "-float")
//          | if (0 != cmd3.!) {
//          |   val str = cmd3.mkString("\\"", "\\" \\"", "\\"\\n")
//          |   throw new Exception("transitivity error map generation tool returned non-zero exit code: " + str)
//          | }
//        """.stripMargin
//      ) set (
//        name        := s"EvaluateTransitivity(${reg.id}).average",
//        imports     += ("java.io.File", "java.nio.file.{Paths, Files}", "scala.sys.process._", "com.andreasschuh.repeat.core._"),
//        usedClasses += IRTK.getClass,
//        inputs      += (regId.toArray, parId.toArray, refId.toArray, ptsMap.toArray),
//        outputs     += (regId, parId, refId, avgMap),
//        calculate   := IRTK.binPath("calculate"),
//        vtk2image   := IRTK.binPath("vtk2image")
//      )
//    )
//  }
//
//  /** Compute total average transitivity error map by averaging the spatially normalized average maps */
//  val summarize = {
//    Capsule(
//      ScalaTask(
//        """
//          | val regId = input.regId.head
//          | val parId = input.parId.head
//          | // TODO
//        """.stripMargin
//      ) set (
//        name    := s"EvaluateTransitivity(${reg.id}).summarize",
//        inputs  += (regId.toArray, parId.toArray),
//        outputs += (regId, parId)
//      )
//    )
//  }
//
//  /** Capsule executed after all sample points have been written to file, i.e., the end of this workflow puzzle */
//  val end =
//    Capsule(
//      EmptyTask() set (
//        name    := s"EvaluateTransitivity(${reg.id}).end",
//        inputs  += regId.toArray,
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
//   * @param desc  Description of workflow puzzle used for output of status messages.
//   *
//   * @return Workflow puzzle which evaluates the voxel-wise transitivity error of the registration.
//   */
//  def apply(start: Option[Capsule] = None, desc: String = "Evaluation of transitivity for {regId=$regId, parId=$parId, refId=$refId}") = {
//    val begin = start getOrElse
//      Capsule(
//        EmptyTask() set (
//          outputs += go,
//          go      := true
//        )
//      )
//    // nop task needed b/c "when" condition cannot directly be placed on exploration transition
//    // (cf. https://github.com/openmole/openmole/issues/69)
//    val nop  = Capsule(EmptyTask(), strainer = true)
//    val eval = forEachPair -< nop -- (evaluate on Env.short by 10 when "refId != im1Id && refId != im2Id") >- (average on Env.short by 5)
//    val cond = Condition("!avgMap.toFile.exists")
//    begin --
//      forEachPar -<
//        forEachRef -<
//          initPaths -- Switch(
//            Case( cond, Display.QSUB(desc) -- eval -- Display.DONE(desc)),
//            Case(!cond, Display.SKIP(desc))
//          ) >-
//        summarize >-
//      end
//  }
//}
