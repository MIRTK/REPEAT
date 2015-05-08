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
import org.openmole.plugin.hook.display._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.source.file.FileSource
import org.openmole.plugin.task.scala._


/**
 * Run registration with different parameters and store results for evaluation
 */
object RunRegistration {

  /**
   * @param reg Registration info
   *
   * @return Workflow puzzle for running the registration and generating the results needed for quality assessment
   */
  def apply(reg: Registration) = {

    import Dataset._
    import Workspace.{dofAff, dofPre, dofSuf}

    val avgTimeCsvPath = FileUtil.join(reg.sumDir, "Time.csv")

    // -----------------------------------------------------------------------------------------------------------------
    // Variables
    val regId   = Val[String] // ID/name of registration
    val parId   = Val[Int]    // Parameter set ID (column index)
    val parVal  = Val[Map[String, String]] // Map from parameter name to value
    val tgtId   = Val[Int]
    val tgtIm   = Val[File]
    val tgtSeg  = Val[File]
    val srcId   = Val[Int]
    val srcIm   = Val[File]
    val srcSeg  = Val[File]
    val iniDof  = Val[File]   // Pre-computed affine transformation
    val preDof  = Val[File]   // Affine transformation converted to input format
    val affDof  = Val[File]   // Affine transformation converted to input format
    val phiDof  = Val[File]   // Output transformation of registration
    val outDof  = Val[File]   // Output transformation converted to IRTK format
    val outIm   = Val[File]   // Deformed source image
    val outSeg  = Val[File]   // Deformed source segmentation
    val outJac  = Val[File]   // Jacobian determinant map
    val runTime = Val[Double] // Runtime of registration command
    val avgTime = Val[Double] // Mean runtime per parameter set

    val setRegId = ScalaTask(s"""val regId = "${reg.id}"""") set (name := "setRegId", outputs += regId)

    // -----------------------------------------------------------------------------------------------------------------
    // Samplings
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal) zipWithIndex parId
    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")

    // -----------------------------------------------------------------------------------------------------------------
    // Pre-registration steps
    val forEachImPair = ExplorationTask(
      imageSampling x
        (tgtIm  in SelectFileDomain(imgDir, imgPre + "${tgtId}" + imgSuf)) x
        (srcIm  in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)) x
        (tgtSeg in SelectFileDomain(segDir, segPre + "${tgtId}" + segSuf)) x
        (srcSeg in SelectFileDomain(segDir, segPre + "${srcId}" + segSuf)) x
        (iniDof in SelectFileDomain(dofAff, dofPre + "${tgtId},${srcId}" + dofSuf))
    ) set (name := "forEachImPair", inputs += regId, outputs += regId)

    val pre = setRegId -- forEachImPair -<
      CopyFilesTo(Workspace.imgDir, tgtIm,  srcIm) --
      CopyFilesTo(Workspace.segDir, tgtSeg, srcSeg) --
      ConvertDofToAff(reg, regId, tgtId, srcId, iniDof, preDof)

    val preEnd = Capsule(EmptyTask() set (name := "preEnd", inputs += preDof.toArray))

    // -----------------------------------------------------------------------------------------------------------------
    // Pairwise registration
    val forEachPar = ExplorationTask(paramSampling) set (
        name    := "forEachPar",
        inputs  += regId,
        outputs += regId
      )

    val incParId = ScalaTask("val parId = input.parId + 1") set (
        name    := "incParId", 
        inputs  += (regId, parId, parVal),
        outputs += (regId, parId, parVal)
      )

    val forEachImPairPerPar = ExplorationTask(
        imageSampling x
          (tgtIm  in SelectFileDomain(Workspace.imgDir, imgPre + "${tgtId}" + imgSuf)) x
          (srcIm  in SelectFileDomain(Workspace.imgDir, imgPre + "${srcId}" + imgSuf))
      ) set (
        name    := "forEachImPair",
        inputs  += (regId, parId, parVal),
        outputs += (regId, parId, parVal)
      )

    val affDofSrc = EmptyTask() set (
        name    := "affDofSrc",
        inputs  += (regId, parId, parVal, tgtId, tgtIm, srcId, srcIm),
        outputs += (regId, parId, parVal, tgtId, tgtIm, srcId, srcIm, affDof)
      ) source FileSource(FileUtil.join(reg.affDir, dofPre + "${tgtId},${srcId}" + reg.affSuf), affDof)

    val regEnd = Capsule(EmptyTask() set (
        name    := "regEnd",
        inputs  += (regId, parId, tgtId, srcId, phiDof, runTime),
        outputs += (regId, parId, tgtId, srcId, phiDof, runTime)
      ))

    val run = setRegId -- forEachPar -< incParId -- forEachImPairPerPar -< affDofSrc --
      RegisterImages (reg, regId, parId, parVal, tgtId, tgtIm, srcId, srcIm, affDof, phiDof, runTime) -- regEnd --
      ConvertPhiToDof(reg, regId, parId, tgtId, srcId, phiDof, outDof)

    val runEnd = Capsule(EmptyTask() set (
        name    := "runEnd",
        inputs  += (regId, parId, tgtId, srcId, outDof),
        outputs += (regId, parId, tgtId, srcId, outDof)
      ))

    // -----------------------------------------------------------------------------------------------------------------
    // Compute mean runtime for each parameter set and save it in CSV file
    val writeAvgTime = ScalaTask(
      s"""
        | val regId   = input.regId.head
        | val parId   = input.parId.head
        | val avgTime = runTime.sum / runTime.filter(t => t > 0).size
      """.stripMargin) set (
        name    := s"${reg.id}-WriteMeanDsc",
        inputs  += (regId.toArray, parId.toArray, runTime.toArray),
        outputs += (regId, parId, avgTime)
      ) hook (
        AppendToCSVFileHook(avgTimeCsvPath, regId, parId, avgTime) set (
          csvHeader := "Registration,Parameters,Mean runtime",
          singleRow := true
        )
      )

    // -----------------------------------------------------------------------------------------------------------------
    // Post-registration steps
    val post =
      (runEnd -- DeformImage    (reg, regId, parId, tgtId, srcId, outDof, outIm )) +
      (runEnd -- DeformLabels   (reg, regId, parId, tgtId, srcId, outDof, outSeg)) +
      (runEnd -- ComputeJacobian(reg, regId, parId, tgtId, srcId, outDof, outJac))

    // -----------------------------------------------------------------------------------------------------------------
    // Complete registration workflow
    (pre >- preEnd) + (preEnd -- run -- runEnd) + (regEnd >- writeAvgTime) + post
  }
}
