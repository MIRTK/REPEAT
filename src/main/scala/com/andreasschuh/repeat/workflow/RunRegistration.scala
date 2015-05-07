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
    val forEachImPairAndPar = ExplorationTask(
      imageSampling x paramSampling x
        (tgtIm  in SelectFileDomain(Workspace.imgDir, imgPre + "${tgtId}" + imgSuf)) x
        (srcIm  in SelectFileDomain(Workspace.imgDir, imgPre + "${srcId}" + imgSuf)) x
        (affDof in SelectFileDomain(reg.affDir, dofPre + "${tgtId},${srcId}" + reg.affSuf))
    ) set (name := "forEachImPairAndPar", inputs += regId, outputs += regId)

    val incParId = ScalaTask("val parId = input.parId + 1") set (
        name    := "incParId", 
        inputs  += parId,
        outputs += parId
      )

    val run = setRegId -- forEachImPairAndPar -< Capsule(incParId, strainer = true) --
      RegisterImages (reg, regId, parId, parVal, tgtId, tgtIm, srcId, srcIm, affDof, phiDof, runTime) --
      ConvertPhiToDof(reg, regId, parId, tgtId, srcId, phiDof, outDof)

    val runEnd = Capsule(EmptyTask() set (
        name    := "runEnd",
        inputs  += (regId, parId, tgtId, srcId, outDof),
        outputs += (regId, parId, tgtId, srcId, outDof)
      ))

    // -----------------------------------------------------------------------------------------------------------------
    // Post-registration steps
    val post =
      (runEnd -- DeformImage    (reg, regId, parId, tgtId, srcId, outDof, outIm )) +
      (runEnd -- DeformLabels   (reg, regId, parId, tgtId, srcId, outDof, outSeg)) +
      (runEnd -- ComputeJacobian(reg, regId, parId, tgtId, srcId, outDof, outJac))

    // TODO: Compute mean runtime for each parameter set and store it in CSV file

    // -----------------------------------------------------------------------------------------------------------------
    // Complete registration workflow
    (pre >- preEnd) + (preEnd -- run -- runEnd) + post
  }
}
