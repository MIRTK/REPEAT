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

package com.andreasschuh.repeat.workflow

import java.nio.file.{Path, Paths}

import scala.language.postfixOps
import scala.language.reflectiveCalls

import com.andreasschuh.repeat.core._
import com.andreasschuh.repeat.puzzle._

import org.openmole.core.dsl._
import org.openmole.plugin.domain.file._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Skip


/**
 * Run registration with different parameters and store results for evaluation
 */
object RunRegistration {

  /**
   * Construct workflow puzzle
   *
   * @param reg Registration info
   *
   * @return Workflow puzzle for running the registration and generating the results needed for quality assessment
   */
  def apply(reg: Registration) = {

    // -----------------------------------------------------------------------------------------------------------------
    // Constants
    import Dataset.{imgDir => _, segDir => _, _}
    import Workspace.{imgDir, segDir, dofAff, dofPre, dofSuf, logDir, logSuf}

    val tgtImPathTemplate  = Paths.get(    imgDir.getAbsolutePath, imgPre + "${tgtId}"          +     imgSuf).toString
    val srcImPathTemplate  = Paths.get(    imgDir.getAbsolutePath, imgPre + "${srcId}"          +     imgSuf).toString
    val tgtSegPathTemplate = Paths.get(    segDir.getAbsolutePath, segPre + "${tgtId}"          +     segSuf).toString
    val srcSegPathTemplate = Paths.get(    segDir.getAbsolutePath, segPre + "${srcId}"          +     segSuf).toString
    val iniDofPathTemplate = Paths.get(    dofAff.getAbsolutePath, dofPre + "${tgtId},${srcId}" +     dofSuf).toString
    val affDofPathTemplate = Paths.get(reg.affDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.affSuf).toString
    val outDofPathTemplate = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" +     dofSuf).toString
    val outJacPathTemplate = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.jacSuf).toString
    val outImPathTemplate  = Paths.get(reg.imgDir.getAbsolutePath, imgPre + "${srcId}-${tgtId}" +     imgSuf).toString
    val outSegPathTemplate = Paths.get(reg.segDir.getAbsolutePath, segPre + "${srcId}-${tgtId}" +     segSuf).toString
    val outLogPathTemplate = Paths.get(    logDir.getAbsolutePath, "${regId}-${parId}", "${tgtId},${srcId}" + logSuf).toString

    val runTimeCsvPath = FileUtil.join(reg.resDir, "Time.csv").getAbsolutePath
    val avgTimeCsvPath = FileUtil.join(reg.sumDir, "Time.csv").getAbsolutePath

    val skipPre = iniDofPathTemplate == affDofPathTemplate

    val outDofEnabled = true
    val outImEnabled  = true
    val outSegEnabled = true
    val outJacEnabled = true

    // -----------------------------------------------------------------------------------------------------------------
    // Variables
    val regId      = Val[String]              // ID/name of registration
    val parIdx     = Val[Int]                 // Parameter set ID ("params" CSV row index)
    val parId      = Val[String]              // Parameter set ID with leading zeros
    val parVal     = Val[Map[String, String]] // Map from parameter name to value
    val tgtId      = Val[Int]                 // ID of target image
    val tgtImPath  = Val[Path]                // Fixed target image
    val srcId      = Val[Int]                 // ID of source image
    val srcImPath  = Val[Path]                // Moving source image
    val iniDofPath = Val[Path]                // Pre-computed affine transformation from target to source
    val affDofPath = Val[Path]                // Affine transformation converted to input format
    val outDofPath = Val[Path]                // Output transformation converted to IRTK format
    val outImPath  = Val[Path]                // Deformed source image
    val outSegPath = Val[Path]                // Deformed source segmentation
    val outJacPath = Val[Path]                // Jacobian determinant map
    val outLogPath = Val[Path]                // Registration command output log file
    val csvTime    = Val[List[String]]        // Runtime CSV written by previous execution
    val runTime    = Val[Array[Double]]       // Runtime of registration command
    val avgTime    = Val[Array[Double]]       // Mean runtime over all registrations for a given set of parameters

    // -----------------------------------------------------------------------------------------------------------------
    // Samplings
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal)
    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")

    // -----------------------------------------------------------------------------------------------------------------
    // Tasks
    val nop = Capsule(EmptyTask() set (name := s"${reg.id}-NOP")).toPuzzle

    val setRegId =
      EmptyTask() set (
        name    := s"${reg.id}-SetRegId",
        outputs += regId,
        regId   := reg.id
      )

    val forEachPar =
      ExplorationTask(paramSampling zipWithIndex parIdx) set (
        name    := s"${reg.id}-ForEachPar",
        inputs  += regId,
        outputs += regId
      )

    val setParId = SetParId(reg, paramSampling, parIdx, parId)

    val forEachImPair = // must *not* be a capsule as it is used more than once!
      ExplorationTask(imageSampling) set (
        name    := s"${reg.id}-ForEachImPair",
        inputs  += (regId, parId, parVal),
        outputs += (regId, parId, parVal)
      )

    val preBegin =
      ScalaTask(
        s"""
          | val iniDofPath = Paths.get(s"$iniDofPathTemplate")
          | val affDofPath = Paths.get(s"$affDofPathTemplate")
        """.stripMargin
      ) set (
        name    := s"${reg.id}-PreBegin",
        imports += "java.nio.file.Paths",
        inputs  += (regId, tgtId, srcId),
        outputs += (regId, tgtId, srcId, iniDofPath, affDofPath)
      )

    val preEnd =
      Capsule(
        EmptyTask() set (
          name    := s"${reg.id}-PreEnd",
          inputs  += regId.toArray,
          outputs += regId
        )
      )

    val setRegIdOrPreEnd = if (skipPre) Capsule(setRegId) else preEnd

    val runBegin =
      ScalaTask(
        s"""
          | val tgtImPath  = Paths.get(s"$tgtImPathTemplate")
          | val srcImPath  = Paths.get(s"$srcImPathTemplate")
          | val affDofPath = Paths.get(s"$affDofPathTemplate")
          | val outDofPath = Paths.get(s"$outDofPathTemplate")
          | val outLogPath = Paths.get(s"$outLogPathTemplate")
        """.stripMargin
      ) set (
        name    := s"${reg.id}-RunBegin",
        imports += "java.nio.file.Paths",
        inputs  += (regId, parId, parVal, tgtId, srcId),
        outputs += (regId, parId, parVal, tgtId, srcId, tgtImPath, srcImPath, affDofPath, outDofPath, outLogPath)
      )

    val runEnd =
      Capsule(
        EmptyTask() set (
          name    := s"${reg.id}-RunEnd",
          inputs  += (regId, parId, tgtId, srcId, outDofPath, runTime),
          outputs += (regId, parId, tgtId, srcId, outDofPath, runTime)
        )
      )

    val writeRunTime =
      ScalaTask(
        s"""
          | if (runTime.sum > .0) {
          |   val csv = new java.io.File(s"$runTimeCsvPath")
          |   val hdr = if (csv.exists) "" else "Target,Source,User,System,Total,Real\\n"
          |   csv.getParentFile.mkdirs()
          |   val fw  = new java.io.FileWriter(csv, true)
          |   try {
          |     fw.write(hdr + tgtId + "," + srcId)
          |     runTime.foreach( t => fw.write(f",$$t%.2f") )
          |     fw.write("\\n")
          |   }
          |   finally fw.close()
          | }
        """.stripMargin
      ) set (
        name    := s"${reg.id}-RegEnd",
        inputs  += (regId, parId, tgtId, srcId, runTime),
        outputs += (regId, parId, tgtId, srcId, runTime)
      )

    val readTimeCsv =
      ScalaTask(
        s"""
          | val regId   = input.regId.head
          | val parId   = input.parId.head
          | val csvTime = fromFile(s"$runTimeCsvPath").getLines().toList
        """.stripMargin
      ) set (
        name    := s"${reg.id}-ReadTimeCsv",
        imports += "scala.io.Source.fromFile",
        inputs  += (regId.toArray, parId.toArray),
        outputs += (regId, parId, csvTime)
      )

    val getRunTime =
      ScalaTask(s"""val runTime = csvTime.view.filter(_.startsWith(s"$$tgtId,$$srcId,")).head.split(",").drop(2).map(_.toDouble)""") set (
        name    := s"${reg.id}-GetRunTime",
        inputs  += (regId, parId, tgtId, srcId, csvTime),
        outputs += (regId, parId, tgtId, srcId, runTime)
      )

    val writeAvgTime =
      ScalaTask(
        s"""
          | val regId   = input.regId.head
          | val parId   = input.parId.head
          | val runTime = input.${runTime.name}.filter(t => t.sum > .0).transpose
          | val numTime = runTime.head.size
          | val avgTime = if (numTime > .0) runTime.map(_.sum / numTime) else Array.fill(runTime.size)(.0)
          | if (numTime == 0)
          |   println(f"WARNING: Mean runtime for $$regId (parId=$$parId) invalid because no registrations were performed")
          | else if (numTime < input.${runTime.name}.size) {
          |   val ratio = input.${runTime.name}.size.toDouble / numTime.toDouble
          |   println(f"WARNING: Mean runtime for $$regId (parId=$$parId) calculated using only $${100 * ratio}%.0f%% of all registrations")
          | }
        """.stripMargin
      ) set (
        name    := s"${reg.id}-WriteAvgTime",
        inputs  += (regId.toArray, parId.toArray, runTime.toArray),
        outputs += (regId, parId, avgTime)
      ) hook (
        AppendToCSVFileHook(avgTimeCsvPath, regId, parId, avgTime) set (
          csvHeader := "Registration,Parameters,User,System,Total,Real",
          singleRow := true
        )
      )

    // -----------------------------------------------------------------------------------------------------------------
    // Workflow

    val convertDofToAff = if (skipPre) nop else
      setRegId -- forEachImPair -< preBegin --
        ConvertDofToAff(reg, regId, tgtId, srcId, iniDofPath, affDofPath) >-
      preEnd

    val registerImages =
      setRegIdOrPreEnd -- forEachPar -< setParId -- forEachImPair -< runBegin --
        Skip(
          RegisterImages(reg, regId, parId, parVal, tgtId, srcId, tgtImPath, srcImPath, affDofPath, outDofPath, outLogPath, runTime),
          s"""
            | def tgtIm  = tgtImPath.toFile()
            | def srcIm  = srcImPath.toFile()
            | def iniDof = new java.io.File(s"$iniDofPathTemplate")
            | def outDof = new java.io.File(s"$outDofPathTemplate")
            | def outIm  = new java.io.File(s"$outImPathTemplate")
            | def outSeg = new java.io.File(s"$outSegPathTemplate")
            | def outJac = new java.io.File(s"$outJacPathTemplate")
            |
            | def updateOutDof = $outDofEnabled &&
            |   outDof.lastModified() < iniDof.lastModified() &&
            |   outDof.lastModified() < tgtIm .lastModified() &&
            |   outDof.lastModified() < srcIm .lastModified()
            | def updateOutIm = $outImEnabled &&
            |   outIm.lastModified() < iniDof.lastModified() &&
            |   outIm.lastModified() < tgtIm .lastModified() &&
            |   outIm.lastModified() < srcIm .lastModified()
            | def updateOutSeg = $outSegEnabled &&
            |   outSeg.lastModified() < iniDof.lastModified() &&
            |   outSeg.lastModified() < tgtIm .lastModified() &&
            |   outSeg.lastModified() < srcIm .lastModified()
            | def updateOutJac = $outJacEnabled &&
            |   outJac.lastModified() < iniDof.lastModified() &&
            |   outJac.lastModified() < tgtIm .lastModified() &&
            |   outJac.lastModified() < srcIm .lastModified()
            |
            | !(updateOutDof || updateOutIm || updateOutSeg || updateOutJac)
          """.stripMargin
        ) --
      runEnd -- writeRunTime >- readTimeCsv -- forEachImPair -< getRunTime >- writeAvgTime

    val deformImage =
      if (outImEnabled)
        runEnd -- DeformImage(reg, regId, parId, tgtId, srcId, tgtImPathTemplate, srcImPathTemplate, outDofPath, outImPath)
      else
        nop

    val deformLabels =
      if (outSegEnabled)
        runEnd -- DeformLabels(reg, regId, parId, tgtId, srcId, tgtSegPathTemplate, srcSegPathTemplate, outDofPath, outSegPath)
      else
        nop

    val calcDetJac =
      if (outJacEnabled)
        runEnd -- ComputeJacobian(reg, regId, parId, tgtId, srcId, tgtImPathTemplate, outDofPath, outJacPath)
      else
        nop

    convertDofToAff + registerImages + deformImage + deformLabels + calcDetJac
  }
}
