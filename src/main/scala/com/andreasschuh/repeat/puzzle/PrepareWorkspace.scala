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
import org.openmole.core.workflow.data.Prototype
import org.openmole.core.workflow.mole.Capsule
import org.openmole.core.workflow.puzzle.Puzzle
import org.openmole.core.workflow.tools.Condition
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern._

import com.andreasschuh.repeat.core.{Environment => Env, WorkSpace, Cmd, IRTK}


/**
 * Prepare workspace files
 */
object PrepareWorkspace {

  /** Get workflow puzzle for setting up the workspace */
  def apply() = new PrepareWorkspace()

  /**
   * Get workflow puzzle for setting up the workspace
   * @param start End capsule of parent workflow puzzle.
   */
  def apply(start: Capsule) = new PrepareWorkspace(Some(start))

}


/**
 * Prepare workspace files
 *
 * @param start End capsule of parent workflow puzzle.
 */
class PrepareWorkspace(start: Option[Capsule] = None) extends Workflow(start) {

  /** Copy files associated with a template ID to workspace */
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

  /** Link or copy image meta-data CSV files of dataset to workspace */
  protected def linkOrCopyImageMetaData(op: String = "copy") =
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

  /** Copy files associated with an image ID to workspace */
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

  /** Link or copy registration parameters CSV files of dataset to workspace */
  protected def linkOrCopyParams(op: String = "copy") =
    Capsule(
      ScalaTask(
        s"""
          | $op(reg.parCsvPath(dataSet.id), dataSpace.parCsv(reg.id))
        """.stripMargin
      ) set (
        name    := wf + s".${op}Params",
        imports += s"com.andreasschuh.repeat.core.FileUtil.$op",
        inputs  += (dataSet, dataSpace, reg)
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
          |                     "-mask", dataSet.imgBkg.toString,
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
          | val cmd = Cmd(calculate, orgImg.toString, "-mask", orgMsk.toString, "-pad", dataSet.imgBkg.toString, "-out", padImg.toString)
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

  /**
   * Obtain reference VTK point set to be transformed
   *
   * This task converts the image to a VTK poly data file which stores the world coordinates of the foreground voxel
   * centers as its points. The points are used for the inverse-consistency and transitivity error evaluation.
   */
  protected def preparePoints(imgId: Prototype[String]) = {
    val image2vtk = Val[String]
    Capsule(
      ScalaTask(
        s"""
          | val refImg = dataSpace.orgImg(${imgId.name})
          | val refMsk = dataSpace.orgMsk(${imgId.name})
          | val refPts = dataSpace.imgPts(${imgId.name})
          | val outDir = refPts.getParent
          | if (outDir != null) Files.createDirectories(outDir)
          | val cmd = Cmd(image2vtk, refImg, refPts, "-mask", refMsk, "-points")
          | if (cmd.run().exitValue() != 0) {
          |   throw new Exception("Image to points conversion command returned non-zero exit code: " + Cmd.toString(cmd))
          | }
        """.stripMargin
      ) set(
        name      := wf + ".preparePoints",
        imports   += ("java.nio.file.Files", "scala.sys.process._", "com.andreasschuh.repeat.core._"),
        inputs    += (dataSpace, imgId),
        image2vtk := IRTK.binPath("image2vtk")
      )
    )
  }

  /** Workflow puzzle */
  private lazy val _puzzle = {

    import Display._

    val shared    = Condition("dataSet.shared")
    val csvShared = if (WorkSpace.linkCsv) shared else Condition.False
    val orgShared = if (WorkSpace.linkOrg) shared else Condition.False
    val refShared = if (WorkSpace.linkRef) shared else Condition.False

    def copyMetaData = {
      val msgVals = Seq(setId)
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
      val copyCond = !csvShared && outdated
      val linkCond =  csvShared && outdated
      val skipCond = !copyCond && !linkCond
      Switch(
        Case(copyCond, QSUB(copyMsg, msgVals: _*) -- Strain(linkOrCopyImageMetaData("copy") on Env.local) -- DONE(copyMsg, msgVals: _*)),
        Case(linkCond, QSUB(linkMsg, msgVals: _*) -- Strain(linkOrCopyImageMetaData("link") on Env.local) -- DONE(linkMsg, msgVals: _*)),
        Case(skipCond, SKIP(skipMsg, msgVals: _*))
      )
    }

    def copyParams = {
      val msgVals = Seq(setId, regId)
      val copyMsg = "Copying parameters for {setId=${setId}, regId=${regId}}"
      val linkMsg = "Linking parameters for {setId=${setId}, regId=${regId}}"
      val skipMsg = "Parameters up-to-date for {setId=${setId}, regId=${regId}}"
      val outdated =
        Condition(
          """
            | import java.nio.file.Files
            |
            | val parCsv    = reg.parCsvPath(dataSet.id).toFile
            | val parCsvCpy = dataSpace.parCsv(reg.id).toFile
            | val parCsvLnk = Files.isSymbolicLink(parCsvCpy.toPath)
            |
            | (!dataSet.shared && parCsvLnk) || parCsvCpy.lastModified < parCsv.lastModified
          """.stripMargin
        )
      val copyCond = !csvShared && outdated
      val linkCond =  csvShared && outdated
      val skipCond = !copyCond && !linkCond
      Switch(
        Case(copyCond, QSUB(copyMsg, msgVals: _*) -- Strain(linkOrCopyParams("copy") on Env.local) -- DONE(copyMsg, msgVals: _*)),
        Case(linkCond, QSUB(linkMsg, msgVals: _*) -- Strain(linkOrCopyParams("link") on Env.local) -- DONE(linkMsg, msgVals: _*)),
        Case(skipCond, SKIP(skipMsg, msgVals: _*))
      )
    }

    def copyImages = {
      val msgVals = Seq(setId, imgId)
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
      val copyCond = !orgShared && outdated
      val linkCond =  orgShared && outdated
      val skipCond = !copyCond && !linkCond
      Switch(
        Case(copyCond, QSUB(copyMsg, msgVals: _*) -- Strain(linkOrCopyImageData("copy") on Env.local) -- DONE(copyMsg, msgVals: _*)),
        Case(linkCond, QSUB(linkMsg, msgVals: _*) -- Strain(linkOrCopyImageData("link") on Env.local) -- DONE(linkMsg, msgVals: _*)),
        Case(skipCond, SKIP(skipMsg, msgVals: _*))
      )
    }

    def copyTemplates = {
      val msgVals = Seq(setId, refId)
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
      val copyCond = !refShared && outdated
      val linkCond =  refShared && outdated
      val skipCond = !copyCond && !linkCond
      Switch(
        Case(copyCond, QSUB(copyMsg, msgVals: _*) -- Strain(linkOrCopyTemplateData("copy") on Env.local) -- DONE(copyMsg, msgVals: _*)),
        Case(linkCond, QSUB(linkMsg, msgVals: _*) -- Strain(linkOrCopyTemplateData("link") on Env.local) -- DONE(linkMsg, msgVals: _*)),
        Case(skipCond, SKIP(skipMsg, msgVals: _*))
      )
    }

    def copyOrMakeMask = {
      val msgVals = Seq(setId, imgId)
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
      val haveMask = Condition("dataSet.mskPath(imgId) != None")
      val copyCond = !orgShared && haveMask && (isLinked || outdated)
      val linkCond =  orgShared && haveMask && outdated
      val makeCond = !haveMask && outdated
      val skipCond = !copyCond && !linkCond && !makeCond
      Switch(
        Case(copyCond, QSUB(copyMsg, msgVals: _*) -- Strain(prepareMask("copy") on Env.local) -- DONE(copyMsg, msgVals: _*)),
        Case(linkCond, QSUB(linkMsg, msgVals: _*) -- Strain(prepareMask("link") on Env.local) -- DONE(linkMsg, msgVals: _*)),
        Case(makeCond, QSUB(makeMsg, msgVals: _*) -- Strain(prepareMask()       on Env.local) -- DONE(makeMsg, msgVals: _*)),
        Case(skipCond, SKIP(skipMsg, msgVals: _*))
      )
    }

    def maskImages = {
      val msgVals = Seq(setId, imgId)
      val maskMsg = "Padding background for {setId=${setId}, imgId=${imgId}}"
      val skipMsg = "Padded image up-to-date for {setId=${setId}, imgId=${imgId}}"
      val maskCond =
        Condition(
          """
            | val orgMsk = dataSpace.orgMsk(imgId).toFile
            | val orgImg = dataSpace.orgImg(imgId).toFile
            | val padImg = dataSpace.padImg(imgId).toFile
            |
            | padImg.lastModified < orgMsk.lastModified || padImg.lastModified < orgImg.lastModified
          """.stripMargin
        )
      val skipCond = !maskCond
      Switch(
        Case(maskCond, QSUB(maskMsg, msgVals: _*) -- Strain(applyMask on Env.local) -- DONE(maskMsg, msgVals: _*)),
        Case(skipCond, SKIP(skipMsg, msgVals: _*))
      )
    }

    def getImgPoints = {
      val msgVals = Seq(setId, imgId)
      val convMsg = "Get voxel-center positions for {setId=${setId}, imgId=${imgId}}"
      val skipMsg = "Reference points up-to-date for {setId=${setId}, imgId=${imgId}}"
      // TODO: Add condition to only extract the points when they are needed for the evaluation
      val convCond =
        Condition(
          """
            | val refImg = dataSpace.orgImg(imgId).toFile
            | val refMsk = dataSpace.orgMsk(imgId).toFile
            | val refPts = dataSpace.imgPts(imgId).toFile
            | refPts.lastModified < refImg.lastModified || refPts.lastModified < refMsk.lastModified
          """.stripMargin
        )
      val skipCond = !convCond
      Switch(
        Case(convCond, QSUB(convMsg, msgVals: _*) -- Strain(preparePoints(imgId) on Env.local) -- DONE(convMsg, msgVals: _*)),
        Case(skipCond, SKIP(skipMsg, msgVals: _*))
      )
    }

    val withDataSet = Slot(nop("withDataSet"))

    (begin -- forEachDataSet -< getDataSet -- getDataSpace -- getRefId -- copyMetaData -- withDataSet) +
      (withDataSet -- copyTemplates >- end) +
      (withDataSet -- forEachImg -< copyImages -- copyOrMakeMask -- maskImages -- getImgPoints >- nop("forEachImgEnd") >- end) +
      (withDataSet -- forEachReg -< getReg -- copyParams >- nop("forEachRegEnd") >- end)
  }

  /** Get workflow puzzle */
  override def puzzle = _puzzle
}
