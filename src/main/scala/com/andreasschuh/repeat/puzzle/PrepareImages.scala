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
import java.nio.file.Path
import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.mole.{Capsule, Hook}
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.source.file._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.{Switch, Case, Skip}

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Workflow puzzle for extraction of foreground region
 */
object PrepareImages {

  /** Get workflow puzzle for extraction of foreground region */
  def apply() = new PrepareImages()
}

/**
 * Extract foreground region of each image
 */
class PrepareImages {

  import Dataset.{imgCsv, imgPre, imgSuf, imgExt, segPre, segSuf, segExt, mskPre, mskSuf, mskExt, bgVal}
  import Variables.{go, imgId}

  val inImgPath = Val[Path]
  val inMskPath = Val[Path]
  val inSegPath = Val[Path]

  val outImgPath = Val[Path]
  val outSegPath = Val[Path]
  val outMskPath = Val[Path]

  /** Explore all images */
  private val forEachImg = {
    val imgIdSampling = CSVSampling(imgCsv)
    imgIdSampling.addColumn("Image ID", imgId)
    Capsule(
      ExplorationTask(imgIdSampling) set (
        name   := "PrepareImages.forEachImg",
        inputs += go
      )
    )
  }

  /** Set paths of workflow puzzle input/output files */
  private val initPaths =
    Capsule(
      ScalaTask(
        s"""
          | val inImgPath = Paths.get("${Dataset.imgDir}", "$imgPre" + imgId + "$imgSuf")
          | val inSegPath = Paths.get("${Dataset.segDir}", "$segPre" + imgId + "$segSuf")
          | val inMskPath = Paths.get("${Dataset.mskDir}", "$mskPre" + imgId + "$mskSuf")
          |
          | val outImgPath = Paths.get("${Workspace.imgDir}", imgId + "$imgExt")
          | val outSegPath = Paths.get("${Workspace.segDir}", imgId + "$segExt")
          | val outMskPath = Paths.get("${Workspace.mskDir}", imgId + "$mskExt")
        """.stripMargin
      ) set (
        name    := "PrepareImages.initPaths",
        imports += "java.nio.file.Paths",
        inputs  += imgId,
        outputs += (imgId, inImgPath, inSegPath, inMskPath, outImgPath, outSegPath, outMskPath)
      )
    )

  /** Copy input mask */
  private val copyMask =
    Capsule(
      ScalaTask(
        """
          | if (Files.exists(inMskPath)) {
          |   val outDir = outMskPath.getParent
          |   if (outDir != null) Files.createDirectories(outDir)
          |   Files.deleteIfExists(outMskPath)
          |   Files.copy(inMskPath, outMskPath)
          | }
        """.stripMargin
      ) set (
        name    := "PrepareImages.copyMask",
        imports += "java.nio.file.Files",
        inputs  += (imgId, inImgPath, outImgPath, inSegPath, outSegPath, inMskPath, outMskPath),
        outputs += (imgId, inImgPath, outImgPath, inSegPath, outSegPath,            outMskPath)
      )
    )

  /** Apply foreground mask */
  private val applyMask = {
    val calculateBin = Val[String]
    Capsule(
      ScalaTask(
        s"""
          | val outDir = outImgPath.getParent
          | if (outDir != null) Files.createDirectories(outDir)
          | val cmd = Cmd(calculateBin, inImgPath.toString, "-mask", outMskPath.toString, "-pad", "${bgVal.toString}" , "-out", outImgPath.toString)
          | if (cmd.run().exitValue != 0) {
          |   throw new Exception("Mask command return non-zero exit code: " + Cmd.toString(cmd))
          | }
        """.stripMargin
      ) set (
        name         := "PrepareImages.applyMask",
        imports      += ("java.nio.file.Files", "scala.sys.process._", "com.andreasschuh.repeat.core._"),
        usedClasses  += Cmd.getClass,
        inputs       += (imgId, inImgPath, outImgPath, inSegPath, outSegPath, outMskPath),
        outputs      += (imgId,            outImgPath, inSegPath, outSegPath, outMskPath),
        calculateBin := IRTK.binPath("calculate")
      )
    )
  }

  /** Copy input image */
  private val copyImage =
    Capsule(
      ScalaTask(
        """
          | if (Files.exists(inImgPath)) {
          |   val outDir = outImgPath.getParent
          |   if (outDir != null) Files.createDirectories(outDir)
          |   Files.deleteIfExists(outImgPath)
          |   Files.copy(inImgPath, outImgPath)
          | }
        """.stripMargin
      ) set (
        name    := "PrepareImages.copyImage",
        imports += "java.nio.file.Files",
        inputs  += (imgId, inImgPath, outImgPath, inSegPath, outSegPath, outMskPath),
        outputs += (imgId,            outImgPath, inSegPath, outSegPath, outMskPath)
      )
    )

  /** Copy segmentation image */
  private val copyLabels =
    Capsule(
      ScalaTask(
        """
          | if (Files.exists(inSegPath)) {
          |   val outDir = outSegPath.getParent
          |   if (outDir != null) Files.createDirectories(outDir)
          |   Files.deleteIfExists(outSegPath)
          |   Files.copy(inSegPath, outSegPath)
          | }
        """.stripMargin
      ) set (
        name    := "PrepareImages.copyLabels",
        imports += "java.nio.file.Files",
        inputs  += (imgId, outImgPath, inSegPath, outSegPath, outMskPath),
        outputs += (imgId, outImgPath,            outSegPath, outMskPath)
      )
    )

  /** Capsule executed at the end of this workflow puzzle */
  val end =
    Capsule(
      EmptyTask() set (
        name    := s"PrepareImages.end",
        inputs  += imgId.toArray,
        outputs += go,
        go      := true
      )
    )

  /**
   * Get workflow puzzle
   *
   * @param begin   End capsule of parent workflow puzzle (if any). Must output a Boolean variable named "go",
   *                which is consumed by the first task of this workflow puzzle.
   * @param message Status message printed for each image to be prepared.
   *
   * @return Workflow puzzle which prepares the input images for registration.
   */
  def apply(begin: Option[Capsule] = None, message: String = "Apply input mask for {imgId=$imgId}") = {

    val applyMaskCond =
      Condition("outMskPath.toFile.lastModified < inMskPath.toFile.lastModified") ||
      Condition("outImgPath.toFile.lastModified < inMskPath.toFile.lastModified")

    begin.getOrElse(Tasks.start) -- forEachImg -< initPaths -- Switch(
      Case( applyMaskCond, Display.QSUB(message) -- copyMask -- applyMask -- Display.DONE(message)),
      Case(!applyMaskCond, Skip(copyImage, "outImgPath.toFile.lastModified >= inImgPath.toFile.lastModified"))
    ) -- Skip(copyLabels, "outSegPath.toFile.lastModified >= inSegPath.toFile.lastModified") >- end
  }
}
