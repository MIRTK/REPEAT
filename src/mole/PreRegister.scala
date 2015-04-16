// =====================================================================================================================
// Registration Performance Assessment Tool (REPEAT)
// OpenMOLE script to for affine pre-alignment of image pairs
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
val logDir = Constants.logDir
val logSuf = Constants.logSuf

// ---------------------------------------------------------------------------------------------------------------------
// Variables
val tgtId  = Val[Int]
val srcId  = Val[Int]
val tgtIm  = Val[File]
val srcIm  = Val[File]
val tgtDof = Val[File]
val srcDof = Val[File]
val iniDof = Val[File]
val outDof = Val[File]
val invDof = Val[File]
val regLog = Val[File]

// ---------------------------------------------------------------------------------------------------------------------
// Exploration task which iterates the image IDs and file paths
val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("ID", tgtId))
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("ID", srcId))

val forEachTuple  = ExplorationTask(
    (tgtIdSampling x srcIdSampling).filter("tgtId < srcId") x
    (tgtIm  in SelectFileDomain(imgDir, imgPre + "${tgtId}" + imgSuf)) x
    (srcIm  in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)) x
    (tgtDof in SelectFileDomain(Path.join(dofDir, "affine"), refId + ",${tgtId}" + dofSuf)) x
    (srcDof in SelectFileDomain(Path.join(dofDir, "affine"), refId + ",${srcId}" + dofSuf))
  ) set (name := "forEachTuple")

// ---------------------------------------------------------------------------------------------------------------------
// Compose transformations from template to subject to get transformation between each pair of images
val iniDofPath = Path.join(dofDir, "initial", "${tgtId},${srcId}" + dofSuf)

val compBegin = EmptyTask() set (
    name    := "compBegin",
    inputs  += (tgtId, tgtIm, tgtDof, srcId, srcIm, srcDof),
    outputs += (tgtId, tgtIm, tgtDof, srcId, srcIm, srcDof, iniDof)
  ) source FileSource(iniDofPath, iniDof)

val compTask = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val dof1   = new java.io.File(workDir, "$refId," + tgtId + "$dofSuf")
    | val dof2   = new java.io.File(workDir, "$refId," + srcId + "$dofSuf")
    | val iniDof = new java.io.File(workDir, tgtId + "," + srcId + "$dofSuf")
    |
    | IRTK.compose(dof1, dof2, iniDof, true, false)
  """.stripMargin) set (
    name        := "compTask",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += (tgtId, tgtIm, srcId, srcIm),
    inputFiles  += (tgtDof, refId + ",${tgtId}" + dofSuf, symLnk),
    inputFiles  += (srcDof, refId + ",${srcId}" + dofSuf, symLnk),
    outputs     += (tgtId, tgtIm, srcId, srcIm),
    outputFiles += ("${tgtId},${srcId}" + dofSuf, iniDof),
    builder => configFile.foreach(builder.addResource(_))
  ) hook CopyFileHook(iniDof, iniDofPath) on parEnv

val compMole = compBegin -- Skip(compTask, "iniDof.lastModified() >= tgtDof.lastModified() && iniDof.lastModified() >= srcDof.lastModified()")

// ---------------------------------------------------------------------------------------------------------------------
// Affine registration
val outDofPath = Path.join(dofDir, "affine", "${tgtId},${srcId}" + dofSuf)
val regLogPath = Path.join(logDir, "affine", "${tgtId},${srcId}" + logSuf)

val affineBegin = EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, iniDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, iniDof, outDof)
  ) source FileSource(outDofPath, outDof)

val affineTask = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val tgt    = new java.io.File(workDir, "$imgPre" + tgtId + "$imgSuf")
    | val src    = new java.io.File(workDir, "$imgPre" + srcId + "$imgSuf")
    | val ini    = new java.io.File(workDir, tgtId + "," + srcId + "$dofSuf")
    | val outDof = new java.io.File(workDir, "result$dofSuf")
    | val regLog = new java.io.File(workDir, "output$logSuf")
    |
    | IRTK.ireg(tgt, src, Some(ini), outDof, Some(regLog),
    |   "Transformation model" -> "Affine",
    |   "No. of resolution levels" -> 2,
    |   "Padding value" -> 0
    | )
  """.stripMargin) set (
    name        := "affineTask",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += (tgtId, srcId),
    inputFiles  += (tgtIm, imgPre + "${tgtId}" + imgSuf, symLnk),
    inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
    inputFiles  += (iniDof, "${tgtId},${srcId}" + dofSuf, symLnk),
    outputFiles += ("result" + dofSuf, outDof),
    outputFiles += ("output" + logSuf, regLog),
    outputs     += (tgtId, tgtIm, srcId, srcIm, iniDof),
    builder => configFile.foreach(builder.addResource(_))
  ) hook (
    CopyFileHook(outDof, outDofPath),
    CopyFileHook(regLog, regLogPath)
  ) on parEnv

val affineMole = affineBegin -- Skip(affineTask, "outDof.lastModified() >= iniDof.lastModified()")

// ---------------------------------------------------------------------------------------------------------------------
// Invert transformation
val invDofPath = Path.join(dofDir, "affine", "${srcId},${tgtId}" + dofSuf)

val invBegin = EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, outDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, outDof, invDof)
  ) source FileSource(invDofPath, invDof)

val invTask = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val dof    = new java.io.File(workDir, tgtId + "," + srcId + "$dofSuf")
    | val invDof = new java.io.File(workDir, srcId + "," + tgtId + "$dofSuf")
    |
    | IRTK.invert(dof, invDof)
  """.stripMargin) set (
    name        += "invTask",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += (tgtId, tgtIm, srcId, srcIm, outDof),
    inputFiles  += (outDof, "${tgtId},${srcId}" + dofSuf, symLnk),
    outputFiles += ("${srcId},${tgtId}" + dofSuf, invDof),
    outputs     += (tgtId, tgtIm, srcId, srcIm, outDof),
    builder => configFile.foreach(builder.addResource(_))
  ) hook CopyFileHook(invDof, invDofPath) on parEnv

val invMole = invBegin -- Skip(invTask, "invDof.lastModified() >= outDof.lastModified()")

// ---------------------------------------------------------------------------------------------------------------------
// Run workflow
val exec = forEachTuple -< compMole -- affineMole -- invMole start
exec.waitUntilEnded()
