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
 * Preprocessing steps
 *
 * - Register all images to template, first rigid then affine
 * - Affinely register all pairs of images using composition of template transformation as initialization
 */
object PreRegistration {

  /** Construct OpenMOLE workflow puzzle */
  def apply() = {

    // -----------------------------------------------------------------------------------------------------------------
    // Configuration
    import Dataset.{refIm => _, _}
    import Workspace.{imgDir => _, segDir => _, refIm => _, _}

    // -----------------------------------------------------------------------------------------------------------------
    // Variables
    val refIm     = Val[File] // Template image
    val refRigDof = Val[File] // Rigid template to image transformation
    val refAffDof = Val[File] // Affine template to image transformation
    val tgtId     = Val[Int]  // Target image ID
    val tgtIm     = Val[File] // Target image
    val tgtDof    = Val[File] // Template to target transformation
    val srcId     = Val[Int]  // Source image ID
    val srcIm     = Val[File] // Source image
    val srcDof    = Val[File] // Template to source transformation
    val iniDof    = Val[File] // Composition: inv(srcDof) o tgtDof
    val affDof    = Val[File] // Affine target to source transformation
    val invDof    = Val[File] // Affine source to target transformation

    // -----------------------------------------------------------------------------------------------------------------
    // Image ID samplings
    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))

    // -----------------------------------------------------------------------------------------------------------------
    // Affine registration of images to template
    val refImSource  = EmptyTask() set (
        name    := "refImSource",
        outputs += refIm
      ) source FileSource(Dataset.refIm.getAbsolutePath, refIm)

    val forEachIm = Capsule(ExplorationTask(
        srcIdSampling x (srcIm in SelectFileDomain(Dataset.imgDir, imgPre + "${srcId}" + imgSuf))
      ) set (name := "forEachIm"), strainer = true)

    val regToTemplate =
      refImSource -- CopyFilesTo(Workspace.refDir, refIm) -- forEachIm -< CopyFilesTo(Workspace.imgDir, srcIm) --
      RegisterToTemplateRigid (refIm, srcId, srcIm, refRigDof) --
      RegisterToTemplateAffine(refIm, srcId, srcIm, refRigDof, refAffDof)

    val regToTemplateEnded = Capsule(EmptyTask() set (name := "regToTemplateEnded", inputs += refAffDof.toArray))

    // -----------------------------------------------------------------------------------------------------------------
    // Affine registration of all image pairs
    val forEachUniquePair = ExplorationTask(
        (tgtIdSampling x srcIdSampling).filter("tgtId < srcId") x
        (tgtIm  in SelectFileDomain(imgDir, imgPre + "${tgtId}" + imgSuf)) x
        (srcIm  in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)) x
        (tgtDof in SelectFileDomain(dofAff, dofPre + refId + ",${tgtId}" + dofSuf)) x
        (srcDof in SelectFileDomain(dofAff, dofPre + refId + ",${srcId}" + dofSuf))
      ) set (name := "forEachUniquePair")

    val regAffine = forEachUniquePair -< CopyFilesTo(Workspace.imgDir, tgtIm, srcIm) --
      ComposeTemplateDofs(tgtId, tgtIm, tgtDof, srcId, srcIm, srcDof, iniDof) --
      RegisterImagesSymAffine(tgtId, tgtIm, srcId, srcIm, iniDof, affDof, invDof)

    // -----------------------------------------------------------------------------------------------------------------
    // TODO: Evaluate label overlap after affine alignment

    // -----------------------------------------------------------------------------------------------------------------
    // Complete workflow
    (regToTemplate >- regToTemplateEnded) + (regToTemplateEnded -- regAffine)
  }
}
