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
import org.openmole.core.workflow.mole.Capsule
import org.openmole.core.workflow.puzzle.Puzzle
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.{Switch, Case, Strain}

import com.andreasschuh.repeat.core.{Environment => Env, Cmd, IRTK}


/**
 * Prepare workspace files
 *
 * @param start End capsule of parent workflow puzzle.
 */
class PrepareWorkspace(start: Option[Capsule] = None) extends Workflow(start) {

  /** Link or copy CSV files of dataset to workspace (if necessary) */
  protected def linkOrCopyCsvFiles(op: String = "copy") =
    Capsule(
      ScalaTask(
        s"""
          | $op(dataSet.imgCsv, dataSpace.imgCsv)
          | $op(dataSet.segCsv, dataSpace.segCsv)
        """.stripMargin
      ) set (
        name    := wf + s".${op}CsvFiles",
        imports += s"com.andreasschuh.repeat.core.FileUtil.$op",
        inputs  += (dataSet, dataSpace)
      )
    )

  /** Copy files associated with a template ID to workspace (if necessary) */
  protected def linkOrCopyTemplateData(op: String = "copy") =
    Capsule(
      ScalaTask(
        s"""
          | $op(dataSet.refPath(dataSet.refId), dataSpace.refImg(dataSet.refId))
        """.stripMargin
      ) set (
        name    := wf + s".${op}TemplateData",
        imports += s"com.andreasschuh.repeat.core.FileUtil.$op",
        inputs  += (dataSet, dataSpace)
      )
    )

  /** Copy files associated with an image ID to workspace (if necessary) */
  protected def linkOrCopyImageData(op: String = "copy") =
    Capsule(
      ScalaTask(
        s"""
          | $op(dataSet.imgPath(imgId), dataSpace.orgImg(imgId))
          | $op(dataSet.segPath(imgId), dataSpace.orgSeg(imgId))
        """.stripMargin
      ) set (
        name    := wf + s".${op}ImageData",
        imports += s"com.andreasschuh.repeat.core.FileUtil.$op",
        inputs  += (dataSet, dataSpace, imgId)
      )
    )

  /** Prepare mask image, i.e., either copy/link input mask or create one using the background threshold */
  protected def prepareMask(op: String = "copy") = {
    val calculate = Val[String]
    Capsule(
      ScalaTask(
        s"""
          | val outMsk = dataSpace.orgMsk(imgId)
          | dataSet.mskPath(imgId) match {
          |   case Some(orgMsk) => $op(orgMsk, outMsk)
          |   case None =>
          |     val outDir = outMsk.getParent
          |     if (outDir != null) Files.createDirectories(outDir)
          |     val cmd = Cmd(calculate, dataSet.imgPath(imgId).toString,
          |                     "-mask", dataSet.padVal.toString,
          |                     "-inside", "1", "-outside", "0",
          |                     "-out", outMsk.toString, "uchar")
          |     if (cmd.run().exitValue != 0) {
          |       throw new Exception("Mask command returned non-zero exit code: " + Cmd.toString(cmd))
          |     }
          | }
        """.stripMargin
      ) set (
        name        := wf + s".prepareMask",
        imports     += ("java.nio.file.Files", "scala.sys.process._"),
        imports     += ("com.andreasschuh.repeat.core.Cmd", s"com.andreasschuh.repeat.core.FileUtil.$op"),
        usedClasses += Cmd.getClass,
        inputs      += (dataSet, dataSpace,imgId),
        calculate   := IRTK.binPath("calculate")
      )
    )
  }

  /** Apply foreground mask and pad background */
  protected def applyMask = {
    val calculate = Val[String]
    Capsule(
      ScalaTask(
        s"""
          | val orgMsk = dataSpace.orgMsk(imgId)
          | val orgImg = dataSpace.orgImg(imgId)
          | val padImg = dataSpace.padImg(imgId)
          |
          | val outDir = padImg.getParent
          | if (outDir != null) Files.createDirectories(outDir)
          |
          | Files.deleteIfExists(padImg) // especially when it is a symbolic link!
          |
          | val cmd = Cmd(calculate, orgImg.toString, "-mask", orgMsk.toString, "-pad", dataSet.padVal.toString, "-out", padImg.toString)
          | if (cmd.run().exitValue != 0) {
          |   throw new Exception("Mask command return non-zero exit code: " + Cmd.toString(cmd))
          | }
        """.stripMargin
      ) set (
        name        := wf + ".applyMask",
        imports     += ("java.nio.file.Files", "scala.sys.process._", "com.andreasschuh.repeat.core.{Cmd, FileUtil}"),
        usedClasses += Cmd.getClass,
        inputs      += (dataSet, dataSpace, imgId),
        calculate   := IRTK.binPath("calculate")
      )
    )
  }

  /** Workflow puzzle */
  private lazy val _puzzle = {

    import Display._

    val shared = Condition("dataSet.shared")

    def copyMetaData = {
      val copyMsg = "Copying meta-data for {setId=${setId}}"
      val linkMsg = "Linking meta-data for {setId=${setId}}"
      val skipMsg = "Meta-data up-to-date for {setId=${setId}}"
      val outdated =
        Condition(
          """
            | import java.nio.file.Files
            |
            | val imgCsv    = dataSet  .imgCsv.toFile
            | val imgCsvCpy = dataSpace.imgCsv.toFile
            | val segCsv    = dataSet  .segCsv.toFile
            | val segCsvCpy = dataSpace.segCsv.toFile
            |
            | val imgCsvLnk = Files.isSymbolicLink(imgCsvCpy.toPath)
            | val segCsvLnk = Files.isSymbolicLink(segCsvCpy.toPath)
            |
            | (!dataSet.shared && imgCsvLnk) || imgCsvCpy.lastModified < imgCsv.lastModified
            | (!dataSet.shared && segCsvLnk) || segCsvCpy.lastModified < segCsv.lastModified
          """.stripMargin
        )
      val copyCond = !shared && outdated
      val linkCond =  shared && outdated
      Switch(
        Case( copyCond, QSUB(copyMsg, setId) -- Strain(linkOrCopyCsvFiles("copy") on Env.local) -- DONE(copyMsg, setId)),
        Case( linkCond, QSUB(linkMsg, setId) -- Strain(linkOrCopyCsvFiles("link") on Env.local) -- DONE(linkMsg, setId)),
        Case(!outdated, SKIP(skipMsg, setId))
      )
    }

    def copyImages = {
      val copyMsg = "Copying image data to workspace for {setId=${setId}, imgId=${imgId}}"
      val linkMsg = "Linking image data to workspace for {setId=${setId}, imgId=${imgId}}"
      val skipMsg = "Image data up-to-date for {setId=${setId}, imgId=${imgId}}"
      val outdated =
        Condition(
          """
            | import java.nio.file.Files
            |
            | val orgImg = dataSet.imgPath(imgId).toFile
            | val orgCpy = dataSpace.orgImg(imgId).toFile
            | val orgLnk = Files.isSymbolicLink(orgCpy.toPath)
            |
            | (!dataSet.shared && orgLnk) || orgCpy.lastModified < orgImg.lastModified
          """.stripMargin
        )
      val copyCond = !shared && outdated
      val linkCond =  shared && outdated
      Switch(
        Case( copyCond, QSUB(copyMsg, setId, imgId) -- Strain(linkOrCopyImageData("copy") on Env.local) -- DONE(copyMsg, setId, imgId)),
        Case( linkCond, QSUB(linkMsg, setId, imgId) -- Strain(linkOrCopyImageData("link") on Env.local) -- DONE(linkMsg, setId, imgId)),
        Case(!outdated, SKIP(skipMsg, setId, imgId))
      )
    }

    def copyTemplates = {
      val copyMsg = "Copying template data to workspace for {setId=${setId}, refId=${refId}}"
      val linkMsg = "Linking template data to workspace for {setId=${setId}, refId=${refId}}"
      val skipMsg = "Template data up-to-date for {setId=${setId}, refId=${refId}}"
      val outdated =
        Condition(
          """
            | import java.nio.file.Files
            |
            | val refImg = dataSet.refPath(refId).toFile
            | val refCpy = dataSpace.refImg(refId).toFile
            | val refLnk = Files.isSymbolicLink(refCpy.toPath)
            |
            | (!dataSet.shared && refLnk) || refCpy.lastModified < refImg.lastModified
          """.stripMargin
        )
      val copyCond = !shared && outdated
      val linkCond =  shared && outdated
      Switch(
        Case( copyCond, QSUB(copyMsg, setId, refId) -- Strain(linkOrCopyTemplateData("copy") on Env.local) -- DONE(copyMsg, setId, refId)),
        Case( linkCond, QSUB(linkMsg, setId, refId) -- Strain(linkOrCopyTemplateData("link") on Env.local) -- DONE(linkMsg, setId, refId)),
        Case(!outdated, SKIP(skipMsg, setId, refId))
      )
    }

    def copyOrMakeMask = {
      val copyMsg = "Copying mask for {setId=${setId}, imgId=${imgId}}"
      val linkMsg = "Linking mask for {setId=${setId}, imgId=${imgId}}"
      val makeMsg = "Making mask for {setId=${setId}, imgId=${imgId}}"
      val skipMsg = "Mask up-to-date for {setId=${setId}, imgId=${imgId}}"
      val outdated =
        Condition(
          """
            | val outMsk = dataSpace.orgMsk(imgId).toFile
            | dataSet.mskPath(imgId) match {
            |   case Some(orgMsk) => outMsk.lastModified < orgMsk.toFile.lastModified
            |   case None =>
            |     val orgImg = dataSpace.orgImg(imgId).toFile
            |     outMsk.lastModified < orgImg.lastModified
            | }
          """.stripMargin
        )
      val isLinked = Condition(
        """
          | import java.nio.file.Files
          | Files.isSymbolicLink(dataSpace.orgMsk(imgId))
        """.stripMargin
      )
      val copyCond = Condition("!dataSet.shared && dataSet.mskPath(imgId) != None") && (isLinked || outdated)
      val linkCond = Condition(" dataSet.shared && dataSet.mskPath(imgId) != None") && outdated
      val makeCond = Condition("dataSet.mskPath(imgId) == None") && outdated
      Switch(
        Case( copyCond, QSUB(copyMsg, setId, imgId) -- Strain(prepareMask("copy") on Env.local) -- DONE(copyMsg, setId, imgId)),
        Case( linkCond, QSUB(linkMsg, setId, imgId) -- Strain(prepareMask("link") on Env.local) -- DONE(linkMsg, setId, imgId)),
        Case( makeCond, QSUB(makeMsg, setId, imgId) -- Strain(prepareMask()       on Env.local) -- DONE(makeMsg, setId, imgId)),
        Case(!outdated, SKIP(skipMsg, setId, imgId))
      )
    }

    def maskImages = {
      val maskMsg = "Padding background for {setId=${setId}, imgId=${imgId}}"
      val skipMsg = "Padded image up-to-date for {setId=${setId}, imgId=${imgId}}"
      val cond =
        Condition(
          """
            | val orgMsk = dataSpace.orgMsk(imgId).toFile
            | val orgImg = dataSpace.orgImg(imgId).toFile
            | val padImg = dataSpace.padImg(imgId).toFile
            |
            | padImg.lastModified < orgMsk.lastModified || padImg.lastModified < orgImg.lastModified
          """.stripMargin
        )
      Switch(
        Case( cond, QSUB(maskMsg, setId, imgId) -- Strain(applyMask on Env.local) -- DONE(maskMsg, setId, imgId)),
        Case(!cond, SKIP(skipMsg, setId, imgId))
      )
    }

    val withDataSet = nop("withDataSet")

    (first -- forEachDataSet -< getDataSet -- getDataSpace -- getRefId -- copyMetaData -- withDataSet) +
      (withDataSet -- copyTemplates >- end) +
      (withDataSet -- forEachImg -< copyImages -- copyOrMakeMask -- maskImages >- nop("forEachImgEnd") >- end)
  }

  /** Get workflow puzzle */
  override def puzzle = _puzzle
}
