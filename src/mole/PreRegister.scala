// =============================================================================
// Project: Registration Performance Assessment Tool (REPEAT)
// Module:  OpenMOLE script for affine pre-alignment
//
// Copyright (c) 2015, Andreas Schuh.
// See LICENSE file for license information.
// =============================================================================

import com.andreasschuh.repeat._

// TODO: Add config file to resources list only if not None!
val configFile: File = GlobalSettings().configFile.get

// Environment on which to execute registrations
val parEnv = Environment.short
val symLnk = Environment.symLnk

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

// Exploration task which iterates the image IDs and file paths
val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("ID", tgtId))
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("ID", srcId))

val forEachTuple  = ExplorationTask(
    (tgtIdSampling x srcIdSampling).filter("tgtId < srcId") +
    (tgtIm  in SelectFileDomain(imgDir, imgPre + "${tgtId}" + imgSuf)) +
    (srcIm  in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)) +
    (tgtDof in SelectFileDomain(Path.join(dofDir, "affine"), refId + ",${tgtId}" + dofSuf)) +
    (srcDof in SelectFileDomain(Path.join(dofDir, "affine"), refId + ",${srcId}" + dofSuf))
  )

// Transformation composition mole
val iniDofPath = Path.join(dofDir, "initial", "${tgtId},${srcId}" + dofSuf)

val compBegin = EmptyTask() set (
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
    resources   += configFile,
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += (tgtId, tgtIm, srcId, srcIm),
    inputFiles  += (tgtDof, refId + ",${tgtId}" + dofSuf, symLnk),
    inputFiles  += (srcDof, refId + ",${srcId}" + dofSuf, symLnk),
    outputs     += (tgtId, tgtIm, srcId, srcIm),
    outputFiles += ("${tgtId},${srcId}" + dofSuf, iniDof)
  ) hook CopyFileHook(iniDof, iniDofPath) on parEnv

val compEnd = Capsule(EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, iniDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, iniDof)
  ))

val compCond = "iniDof.lastModified() < tgtDof.lastModified() || iniDof.lastModified() < srcDof.lastModified()"
val compMole = compBegin -- ((compTask -- compEnd) when compCond, compEnd when s"!($compCond)")

// Affine registration mole
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
    |   "Background value" -> 0,
    |   "Padding value" -> 0
    | )
  """.stripMargin) set (
    resources   += configFile,
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += (tgtId, srcId),
    inputFiles  += (tgtIm, imgPre + "${tgtId}" + imgSuf, symLnk),
    inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
    inputFiles  += (iniDof, "${tgtId},${srcId}" + dofSuf, symLnk),
    outputFiles += ("result" + dofSuf, outDof),
    outputFiles += ("output" + logSuf, regLog),
    outputs     += (tgtId, tgtIm, srcId, srcIm, iniDof)
  ) hook (
    CopyFileHook(outDof, outDofPath),
    CopyFileHook(regLog, regLogPath)
  ) on parEnv

val affineEnd = Capsule(EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, outDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, outDof)
  ))

val affineCond = "outDof.lastModified() < iniDof.lastModified()"
val affineMole = affineBegin -- ((affineTask -- affineEnd) when affineCond, affineEnd when s"!($affineCond)")

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
    resources   += configFile,
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += (tgtId, tgtIm, srcId, srcIm, outDof),
    inputFiles  += (outDof, "${tgtId},${srcId}" + dofSuf, symLnk),
    outputFiles += ("${srcId},${tgtId}" + dofSuf, invDof),
    outputs     += (tgtId, tgtIm, srcId, srcIm, outDof)
  ) hook CopyFileHook(invDof, invDofPath) on parEnv

val invEnd = Capsule(EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, outDof, invDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, outDof, invDof)
  ))

val invCond = "invDof.lastModified() < outDof.lastModified()"
val invMole = invBegin -- ((invTask -- invEnd) when invCond, invEnd when s"!($invCond)")

// Run affine registration pipeline for each pair of images
val exec = (forEachTuple -< compMole) + (compEnd -- affineMole) + (affineEnd -- invMole) start
exec.waitUntilEnded()
