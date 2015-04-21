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
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.source.file.FileSource


/**
 * Run registration with different parameters and store results for evaluation
 */
object RunRegistration {
  def apply(regId: String) = {

    import Dataset._
    import Workspace.{dofAff, dofPre, dofSuf}

    val reg = Registration(regId)

    val parId  = Val[Int]                 // Parameter set ID (column index)
    val parVal = Val[Map[String, String]] // Map from parameter name to value
    val tgtId  = Val[Int]
    val tgtIm  = Val[File]
    val tgtSeg = Val[File]
    val srcId  = Val[Int]
    val srcIm  = Val[File]
    val srcSeg = Val[File]
    val iniDof = Val[File] // Pre-computed affine transformation
    val affDof = Val[File] // Affine transformation converted to input format
    val phiDof = Val[File] // Output transformation of registration
    val outDof = Val[File] // Output transformation converted to IRTK format

    val forEachImPair = {
      val paramSampling = CSVToMapSampling(reg.parCsv, parVal) zipWithIndex parId
      val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
      val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
      val imageSampling = {
        (tgtIdSampling x srcIdSampling).filter(if (reg.isSym) "tgtId < srcId" else "tgtId != srcId") x
        (tgtIm  in SelectFileDomain(imgDir, imgPre + "${tgtId}" + imgSuf)) x
        (srcIm  in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)) x
        (tgtSeg in SelectFileDomain(segDir, segPre + "${tgtId}" + segSuf)) x
        (srcSeg in SelectFileDomain(segDir, segPre + "${srcId}" + segSuf)) x
        (iniDof in SelectFileDomain(dofAff, dofPre + "${tgtId},${srcId}" + dofSuf))
      }
      ExplorationTask(imageSampling x paramSampling) set (name := "forEachImPair")
    }

    val regAll = forEachImPair -<
      CopyFilesTo(Workspace.imgDir, tgtIm, tgtSeg, srcIm, srcSeg) --
      ConvertDofToAff(reg, iniDof, affDof) --
      RegisterImages (reg, parVal, tgtId, tgtIm, srcId, srcIm, affDof, phiDof) --
      ConvertPhiToDof(reg, phiDof, outDof)
  }
}
