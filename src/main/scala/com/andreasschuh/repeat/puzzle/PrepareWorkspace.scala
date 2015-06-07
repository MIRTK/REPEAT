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
import org.openmole.plugin.tool.pattern.{Switch, Case}

import com.andreasschuh.repeat.core.{Environment => Env, Cmd, IRTK}


/**
 * Prepare workspace files
 *
 * @param begin End capsule of parent workflow puzzle.
 */
class PrepareWorkspace(start: Option[Capsule] = None) extends Workflow(start) {

  /** Copy CSV files of dataset to workspace (if necessary) */
  protected def copyCsvFiles =
    Capsule(
      ScalaTask(
        """
          | copy(dataSet.imgCsv, dataSpace.imgCsv)
          | copy(dataSet.segCsv, dataSpace.segCsv)
        """.stripMargin
      ) set (
        name    := wf + ".copyCsvFiles",
        imports += "com.andreasschuh.repeat.core.FileUtil.copy",
        inputs  += (dataSet, dataSpace)
      ),
      strainer = true
    )

  /** Copy files associated with a template ID to workspace (if necessary) */
  protected def copyTemplateData =
    Capsule(
      ScalaTask(
        """
          | copy(dataSet.refPath(dataSet.refId), dataSpace.refImg(dataSet.refId))
        """.stripMargin
      ) set (
        name    := wf + ".copyTemplateData",
        imports += "com.andreasschuh.repeat.core.FileUtil.copy",
        inputs  += (dataSet, dataSpace)
      ),
      strainer = true
    )

  /** Copy files associated with an image ID to workspace (if necessary) */
  protected def copyImageData =
    Capsule(
      ScalaTask(
        """
          | copy(dataSet.imgPath(imgId), dataSpace.orgImg(imgId))
          | copy(dataSet.imgPath(imgId), dataSpace.orgImg(imgId))
          | copy(dataSet.imgPath(imgId), dataSpace.orgImg(imgId))
        """.stripMargin
      ) set (
        name    := wf + ".copyImageData",
        imports += "com.andreasschuh.repeat.core.FileUtil.copy",
        inputs  += (dataSet, dataSpace, imgId)
      ),
      strainer = true
    )

  /** Apply foreground mask and pad background */
  protected def applyMask = {
    val calculate = Val[String]
    Capsule(
      ScalaTask(
        s"""
          | val orgImg = dataSet.imgPath(imgId)
          | val orgMsk = dataSet.mskPath(imgId)
          | val padImg = dataSpace.padImg(imgId)
          |
          | val outDir = padImg.getParent
          | if (outDir != null) Files.createDirectories(outDir)
          |
          | val cmd = Cmd(calculate, orgImg.toString, "-mask", orgMsk.toString, "-pad", dataSet.padVal.toString, "-out", padImg.toString)
          | if (cmd.run().exitValue != 0) {
          |   throw new Exception("Mask command return non-zero exit code: " + Cmd.toString(cmd))
          | }
        """.stripMargin
      ) set (
        name        := wf + ".applyMask",
        imports     += ("java.nio.file.Files", "scala.sys.process._", "com.andreasschuh.repeat.core.Cmd"),
        usedClasses += Cmd.getClass,
        inputs      += (dataSet, dataSpace, imgId),
        calculate   := IRTK.binPath("calculate")
      ),
      strainer = true
    )
  }

  /** Workflow puzzle */
  private lazy val puzzle = {

    def copyMetaData = {
      val what = "Copying meta-data for {setId=${setId}}"
      val cond =
        Condition(
          """
            | val imgCsv    = dataSet  .imgCsv.toFile
            | val imgCsvCpy = dataSpace.imgCsv.toFile
            | val segCsv    = dataSet  .segCsv.toFile
            | val segCsvCpy = dataSpace.segCsv.toFile
            | (imgCsvCpy != imgCsv && imgCsvCpy.lastModified < imgCsv.lastModified) ||
            | (segCsvCpy != segCsv && segCsvCpy.lastModified < segCsv.lastModified)
          """.stripMargin
        )
      Switch(
        Case( cond, Display.QSUB(what) -- (copyCsvFiles on Env.local) -- Display.DONE(what)),
        Case(!cond, Display.SKIP(what))
      )
    }

    def copyImages = {
      val what = "Copying image data to workspace for {setId=${setId}, imgId=${imgId}}"
      val cond =
        Condition(
          """
            | val orgImg = dataSet.imgPath(imgId).toFile
            | val orgCpy = dataSpace.orgImg(imgId).toFile
            | orgCpy != orgImg && orgCpy.lastModified < orgImg.lastModified
          """.stripMargin
        )
      Switch(
        Case( cond, Display.QSUB(what) -- (copyImageData on Env.local) -- Display.DONE(what)),
        Case(!cond, Display.SKIP(what))
      )
    }

    def copyTemplates = {
      val what = "Copying template data to workspace for {setId=${setId}, refId=${refId}}"
      val cond =
        Condition(
          """
            | val refImg = dataSet.refPath(refId).toFile
            | val refCpy = dataSpace.refImg(refId).toFile
            | refCpy != refImg && refCpy.lastModified < refImg.lastModified
          """.stripMargin
        )
      Switch(
        Case( cond, Display.QSUB(what) -- (copyTemplateData on Env.local) -- Display.DONE(what)),
        Case(!cond, Display.SKIP(what))
      )
    }

    def maskImages = {
      val what = "Extracting background for {setId=${setId}, imgId=${imgId}}"
      val cond =
        Condition(
          """
            | val orgMsk = dataSet.mskPath(imgId).toFile
            | val orgImg = dataSet.imgPath(imgId).toFile
            | val padImg = dataSpace.padImg(imgId).toFile
            | orgMsk.exists && (padImg.lastModified < orgMsk.lastModified || padImg.lastModified < orgImg.lastModified)
          """.stripMargin
        )
      Switch(
        Case( cond, Display.QSUB(what) -- (applyMask on Env.local) -- Display.DONE(what)),
        Case(!cond, Display.SKIP(what))
      )
    }

    val withDataSet      = getDataSet -- getDataSpace -- copyMetaData
    def exploreDataSets  = first -- forEachDataSet -< withDataSet
    def prepareTemplates = withDataSet -< copyTemplates >- end
    def prepareImages    = withDataSet -- forEachImg -< (copyImages, maskImages) >- end

    Puzzle.merge(first, Seq(end), puzzles = Seq(exploreDataSets, prepareTemplates, prepareImages))
  }

  /** Get workflow puzzle */
  override def toPuzzle = puzzle
}
