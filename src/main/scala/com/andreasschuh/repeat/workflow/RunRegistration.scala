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

import java.io.File
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

    import Dataset._
    import Workspace.{dofAff, dofPre, dofSuf}

    val runTimeCsvPath = FileUtil.join(reg.resDir, "Time.csv")
    val avgTimeCsvPath = FileUtil.join(reg.sumDir, "Time.csv")

    // -----------------------------------------------------------------------------------------------------------------
    // Variables
    val regId   = Val[String]              // ID/name of registration
    val parId   = Val[Int]                 // Parameter set ID ("params" CSV row index)
    val parVal  = Val[Map[String, String]] // Map from parameter name to value
    val tgtId   = Val[Int]                 // ID of target image
    val tgtIm   = Val[File]                // Fixed target image
    val srcId   = Val[Int]                 // ID of source image
    val srcIm   = Val[File]                // Moving source image
    val iniDof  = Val[File]                // Pre-computed affine transformation from target to source
    val affDof  = Val[File]                // Affine transformation converted to input format
    val phiDof  = Val[File]                // Output transformation of registration
    val outDof  = Val[File]                // Output transformation converted to IRTK format
    val outIm   = Val[File]                // Deformed source image
    val outSeg  = Val[File]                // Deformed source segmentation
    val outJac  = Val[File]                // Jacobian determinant map
    val csvTime = Val[List[String]]        // Runtime CSV written by previous execution
    val runTime = Val[Array[Double]]       // Runtime of registration command
    val avgTime = Val[Array[Double]]       // Mean runtime over all registrations for a given set of parameters

    // -----------------------------------------------------------------------------------------------------------------
    // Samplings
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal)
    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")

    // -----------------------------------------------------------------------------------------------------------------
    // Tasks
    val forEachPar =
      ExplorationTask(paramSampling zipWithIndex parId) set (
        name    := s"${reg.id}-ForEachPar",
        outputs += regId,
        regId   := reg.id
      )

    val incParId =
      Capsule(
        ScalaTask("val parId = input.parId + 1") set (
          name    := s"${reg.id}-IncParId",
          inputs  += parId,
          outputs += parId
        ),
        strainer = true
      )

    val forEachImPair = // must *not* be a capsule as it is used more than once!
      ExplorationTask(
        imageSampling x
          (tgtIm  in SelectFileDomain(imgDir, imgPre + "${tgtId}" + imgSuf)) x
          (srcIm  in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)) x
          (iniDof in SelectFileDomain(dofAff, dofPre + "${tgtId},${srcId}" + dofSuf))
      ) set (
        name := s"${reg.id}-ForEachImPair"
      )

    val writeTimeCsv =
      Capsule(
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
          outputs += (regId, parId, tgtId, srcId)
        ),
        strainer = true
      )

    val runEnd =
      Capsule(
        EmptyTask() set (
          name    := s"${reg.id}-RunEnd",
          inputs  += (regId, parId, tgtId, srcId, outDof),
          outputs += (regId, parId, tgtId, srcId, outDof)
        ),
        strainer = true
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
    val run =
      forEachPar -< incParId -- Capsule(forEachImPair, strainer = true) -<
        ConvertDofToAff(reg, regId,                tgtId,        srcId, iniDof, affDof) --
        RegisterImages (reg, regId, parId, parVal, tgtId, tgtIm, srcId, srcIm,  affDof, phiDof, runTime) -- writeTimeCsv --
        ConvertPhiToDof(reg, regId, parId,         tgtId,        srcId,                 phiDof, outDof ) --
      runEnd

    val post =
      // read time entries from CSV instead of using those from the regEnd aggregation as some registrations
      // may not have been performed because the results were already available (i.e., runTime == 0)
      (writeTimeCsv >- readTimeCsv -- Capsule(forEachImPair, strainer = true) -< getRunTime >- writeAvgTime) +
      (runEnd -- DeformImage    (reg, regId, parId, tgtId, srcId, outDof, outIm )) +
      (runEnd -- DeformLabels   (reg, regId, parId, tgtId, srcId, outDof, outSeg)) +
      (runEnd -- ComputeJacobian(reg, regId, parId, tgtId, srcId, outDof, outJac))

    run + post
  }
}
