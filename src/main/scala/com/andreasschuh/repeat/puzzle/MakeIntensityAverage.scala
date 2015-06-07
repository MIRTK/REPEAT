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
// * Workflow puzzle for creation of intensity-average image
// */
//object MakeIntensityAverage extends Configurable("evaluation.intensity-average") {
//
//  /** Whether to create intensity-average image */
//  val enabled = getBooleanProperty("enabled")
//
//  /** Output directory of voxel-wise intensity average images */
//  val outDir = FileUtil.normalize(FileUtil.join(Workspace.dir, getStringProperty(".workspace.output.atlases")))
//
//  /** Get workflow puzzle for creation of intensity-average image for specified registration */
//  def apply(reg: Registration) = new MakeIntensityAverage(reg)
//}
//
///**
// * Make voxel-wise intensity-average image
// *
// * @param reg Registration used to create intensity-average image.
// */
//class MakeIntensityAverage(reg: Registration) {
//
//  import Dataset.{imgCsv, imgPre, imgSuf, imgExt}
//  import Workspace.{imgDir, dofPre}
//  import MakeIntensityAverage.outDir
//  import Variables.{go, regId, parId, refId, imgId}
//
//  val refImg = Val[Path]
//  val outImg = Val[File]
//  val avgImg = Val[Path]
//
//  /** Explore all parameter settings of the registration */
//  private val forEachPar = {
//    val sampling = CSVSampling(reg.parCsv) set (columns += ("ID", parId))
//    Capsule(
//      ExplorationTask(sampling) set (
//        name    := s"MakeIntensityAverage(${reg.id}).forEachReg",
//        inputs  += go,
//        outputs += regId,
//        regId   := reg.id
//      )
//    )
//  }
//
//  /** Explore all reference images used for voxel-wise intensity-average generation */
//  // TODO: Only use template of dataset as reference (requires population template)
//  private val forEachRef = {
//    val sampling = CSVSampling(imgCsv) set (columns += ("Image ID", refId))
//    Capsule(
//      ExplorationTask(sampling) set (
//        name    := s"MakeIntensityAverage(${reg.id}).forEachRef",
//        inputs  += (regId, parId),
//        outputs += (regId, parId)
//      )
//    )
//  }
//
//  /** Set paths of workflow puzzle input/output files */
//  private val initPaths =
//    Capsule(
//      ScalaTask(
//        s"""
//          | val avgImg = Paths.get(s"$outDir", refId + "$imgExt")
//        """.stripMargin
//      ) set (
//        name    := s"MakeIntensityAverage(${reg.id}).initPaths",
//        imports += "java.nio.file.Paths",
//        inputs  += (regId, parId, refId),
//        outputs += (regId, parId, refId, avgImg)
//      )
//    )
//
//  /** Explore all source images which were registered to the reference image */
//  private val forEachImg = {
//    val sampling = CSVSampling(imgCsv) set (columns += ("Image ID", imgId))
//    Capsule(
//      ExplorationTask(sampling) set (
//        name    := s"MakeIntensityAverage(${reg.id}).forEachImg",
//        inputs  += (regId, parId, refId),
//        outputs += (regId, parId, refId)
//      )
//    )
//  }
//
//  /** Deform image to reference */
//  private val deformImage = {
//    val deformCmd = Val[Cmd]
//    Capsule(
//      ScalaTask(
//        s"""
//          | val refImg = Paths.get(s"$imgDir", "$imgPre" + refId + "$imgSuf")
//          | val srcImg = Paths.get(s"$imgDir", "$imgPre" + imgId + "$imgSuf")
//          | val outDof = Paths.get(s"${reg.dofDir}", "$dofPre" + refId + "," + imgId + "${reg.dofSuf}")
//          | val outImg = new File(workDir, imgId + "-" + refId + "$imgExt")
//          |
//          | val args = Map(
//          |   "target" -> refImg.toString,
//          |   "source" -> srcImg.toString,
//          |   "phi"    -> outDof.toString,
//          |   "out"    -> outImg.toString
//          | )
//          | val cmd = Cmd(deformCmd, args)
//          | if (0 != cmd.!) {
//          |   throw new Exception("Image transformation command returned non-zero exit code: " + Cmd.toString(cmd))
//          | }
//        """.stripMargin
//      ) set (
//        name       := s"MakeIntensityAverage(${reg.id}).deformImage",
//        imports     += ("java.io.File", "java.nio.file.{Paths, Files}", "com.andreasschuh.repeat.core._"),
//        usedClasses += Cmd.getClass,
//        inputs      += (regId, parId, refId, imgId),
//        outputs     += (regId, parId, refId, imgId, outImg),
//        deformCmd   := reg.deformImageCmd
//      )
//    )
//  }
//
//  /** Average the intensities of all images */
//  private val makeAverage = {
//    val calculate = Val[String]
//    Capsule(
//      ScalaTask(
//        s"""
//          | val avgImg = Paths.get(s"$outDir", refId + "$imgExt")
//          |
//          | val outDir = avgImg.getParent
//          | if (outDir != null) Files.createDirectories(outDir)
//          |
//          | val cmd = Cmd(calculate, outImg.head) ++
//          |             outImg.tail.flatMap(img => Cmd("+", img.toString)) ++
//          |             Cmd("/", outImg.size.toString, "=", avgImg.toString)
//          | if (0 != cmd.!) {
//          |   throw new Exception("Voxel-wise intensity average command returned non-zero exit code: " + Cmd.toString(cmd))
//          | }
//        """.stripMargin
//      ) set (
//        name        := s"MakeIntensityAverage(${reg.id}).makeBiasedAverage",
//        imports     += ("java.io.File", "java.nio.file.{Paths, Files}", "scala.sys.process._", "com.andreasschuh.repeat.core._"),
//        usedClasses += Cmd.getClass,
//        inputs      += (regId, parId, refId, outImg.toArray),
//        outputs     += (regId, parId, refId, avgImg),
//        calculate   := IRTK.binPath("calculate")
//      )
//    )
//  }
//
//  /** Compute unbiased intensity average image by averaging the biased intensity average images */
//  val makeUnbiasedAverage = {
//    Capsule(
//      ScalaTask(
//        """
//          | val regId = input.regId.head
//          | val parId = input.parId.head
//          | // TODO
//        """.stripMargin
//      ) set (
//        name    := s"MakeIntensityAverage(${reg.id}).makeUnbiasedAverage",
//        inputs  += (regId.toArray, parId.toArray),
//        outputs += (regId, parId)
//      )
//    )
//  }
//
//  /** Capsule executed after all intensity average images have been written to file, i.e., the end of this workflow puzzle */
//  val end =
//    Capsule(
//      EmptyTask() set (
//        name    := s"MakeIntensityAverage(${reg.id}).end",
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
//   * @return Workflow puzzle which makes the voxel-wise intensity-average image(s) using the registration result.
//   */
//  def apply(start: Option[Capsule] = None, desc: String = "Make intensity average image for {regId=$regId, parId=$parId, refId=$refId}") = {
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
//    val main = forEachImg -< nop -- (deformImage on Env.short by 10 when "refId != imgId") >- (makeAverage on Env.short)
//    val cond = Condition("!avgMap.toFile.exists")
//    begin --
//      forEachPar -<
//        forEachRef -<
//          initPaths -- Switch(
//            Case( cond, Display.QSUB(desc) -- main -- Display.DONE(desc)),
//            Case(!cond, Display.SKIP(desc))
//          ) >-
//        makeUnbiasedAverage >-
//      end
//  }
//}
