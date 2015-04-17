// =====================================================================================================================
// Registration Performance Assessment Tool (REPEAT)
// OpenMOLE script for initial spatial normalization to MNI space
//
// Copyright (C) 2015  Andreas Schuh
//
//   This program is free software: you can redistribute it and/or modify
//   it under the terms of the GNU Affero General Public License as published by
//   the Free Software Foundation, either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU Affero General Public License for more details.
//
//   You should have received a copy of the GNU Affero General Public License
//   along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
// =====================================================================================================================

// ---------------------------------------------------------------------------------------------------------------------
// Import packages
import java.io.File
import com.andreasschuh.repeat._

// ---------------------------------------------------------------------------------------------------------------------
// Resources
val configFile = GlobalSettings().configFile

// ---------------------------------------------------------------------------------------------------------------------
// Environment on which to execute registrations
val parEnv = Environment.short
val symLnk = Environment.symLnk

// ---------------------------------------------------------------------------------------------------------------------
// Constants
val refId  = Constants.refId
val imgCsv = Constants.imgCsv
val imgDir = Constants.imgIDir
val imgPre = Constants.imgPre
val imgSuf = Constants.imgSuf
val dofSuf = Constants.dofSuf
val dofDir = Constants.dofDir
val dofRig = Constants.dofRigid
val dofIni = Constants.dofInitial
val dofAff = Constants.dofAffine
val logDir = Constants.logDir
val logSuf = Constants.logSuf

// ---------------------------------------------------------------------------------------------------------------------
// Variables
val refIm    = Val[File]
val srcId    = Val[Int]
val srcIm    = Val[File]
val dof6     = Val[File]
val dof6Log  = Val[File]
val dof12    = Val[File]
val dof12Log = Val[File]

// ---------------------------------------------------------------------------------------------------------------------
// Exploration task which iterates the image IDs and file paths
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
val forEachIm = ExplorationTask(
    srcIdSampling x
    (refIm in SelectFileDomain(Constants.refIm.getParentFile, Constants.refIm.getName)) x
    (srcIm in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf))
  ) set (name := "forEachIm")

// ---------------------------------------------------------------------------------------------------------------------
// Rigid registration mole
val dof6Path    = Path.join(dofDir, dofRig, refId + ",${srcId}" + dofSuf)
val dof6LogPath = Path.join(logDir, dofRig, refId + ",${srcId}" + logSuf)

val rigidBegin = EmptyTask() set (
    name    := "rigidBegin",
    inputs  += (refIm, srcId, srcIm),
    outputs += (refIm, srcId, srcIm, dof6)
  ) source FileSource(dof6Path, dof6)

val rigidReg = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val tgt     = new java.io.File(workDir, "${Constants.refIm.getName}")
    | val src     = new java.io.File(workDir, "$imgPre" + srcId + "$imgSuf")
    | val dof6    = new java.io.File(workDir, "result$dofSuf")
    | val dof6Log = new java.io.File(workDir, "output$logSuf")
    |
    | IRTK.ireg(tgt, src, None, dof6, Some(dof6Log),
    |   "Transformation model" -> "Rigid",
    |   "Background value" -> 0
    | )
  """.stripMargin) set (
    name        := "rigidReg",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += srcId,
    inputFiles  += (refIm, Constants.refIm.getName, symLnk),
    inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
    outputFiles += ("result" + dofSuf, dof6),
    outputFiles += ("output" + logSuf, dof6Log),
    outputs     += (refIm, srcId, srcIm),
    taskBuilder => configFile.foreach(taskBuilder.addResource(_))
  ) hook (
    CopyFileHook(dof6,    dof6Path),
    CopyFileHook(dof6Log, dof6LogPath)
  ) on parEnv

val rigidMole = rigidBegin -- Skip(rigidReg, "dof6.exists()")

// ---------------------------------------------------------------------------------------------------------------------
// Affine registration mole
val dof12Path    = Path.join(dofDir, dofAff, refId + ",${srcId}" + dofSuf)
val dof12LogPath = Path.join(logDir, dofAff, refId + ",${srcId}" + logSuf)

val affineBegin = EmptyTask() set (
    name    := "affineBegin",
    inputs  += (refIm, srcId, srcIm, dof6),
    outputs += (refIm, srcId, srcIm, dof6, dof12)
  ) source FileSource(dof12Path, dof12)

val affineReg = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val tgt      = new java.io.File(workDir, "${Constants.refIm.getName}")
    | val src      = new java.io.File(workDir, "$imgPre" + srcId + "$imgSuf")
    | val ini      = new java.io.File(workDir, "$refId," + srcId + "$dofSuf")
    | val dof12    = new java.io.File(workDir, "result$dofSuf")
    | val dof12Log = new java.io.File(workDir, "output$logSuf")
    |
    | IRTK.ireg(tgt, src, Some(ini), dof12, Some(dof12Log),
    |   "Transformation model" -> "Affine",
    |   "Padding value" -> 0
    | )
  """.stripMargin) set (
    name        := "affineReg",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += srcId,
    inputFiles  += (refIm, Constants.refIm.getName, symLnk),
    inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
    inputFiles  += (dof6, refId + ",${srcId}" + dofSuf, symLnk),
    outputFiles += ("result" + dofSuf, dof12),
    outputFiles += ("output" + logSuf, dof12Log),
    outputs     += (refIm, srcId, srcIm),
    taskBuilder => configFile.foreach(taskBuilder.addResource(_))
  ) hook (
    CopyFileHook(dof12,    dof12Path),
    CopyFileHook(dof12Log, dof12LogPath)
  ) on parEnv

val affineMole = affineBegin -- Skip(affineReg, "dof12.lastModified() >= dof6.lastModified()")

// ---------------------------------------------------------------------------------------------------------------------
// Run workflow
val exec = forEachIm -< rigidMole -- affineMole start
exec.waitUntilEnded()
