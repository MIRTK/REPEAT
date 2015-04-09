// =============================================================================
// Project: Registration Performance Assessment Tool (REPEAT)
// Module:  OpenMOLE script for initial spatial normalization to MNI space
//
// Copyright (c) 2015, Andreas Schuh.
// See LICENSE file for license information.
// =============================================================================

import com.andreasschuh.repeat._

// Environment on which to execute registrations
val parEnv = Workflow.parEnv
val symLnk = Workflow.symLnk

// Constants
val refId  = Workflow.refId
val imgCsv = Workflow.imgCsv
val imgDir = Workflow.imgIDir
val imgPre = Workflow.imgPre
val imgSuf = Workflow.imgSuf
val dofSuf = Workflow.dofSuf
val dofDir = Workflow.dofDir
val logDir = Workflow.logDir
val logSuf = Workflow.logSuf

// Variables
val srcId    = Val[Int]
val srcIm    = Val[File]
val dof6     = Val[File]
val dof6Log  = Val[File]
val dof12    = Val[File]
val dof12Log = Val[File]

// Exploration task which iterates the image IDs and file paths
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("ID", srcId))
val forEachIm     = ExplorationTask(srcIdSampling + (srcIm in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)))

// Rigid registration mole
val dof6Template    = Path.join(dofDir, "rigid", refId + ",${srcId}" + dofSuf)
val dof6LogTemplate = Path.join(logDir, "rigid", refId + ",${srcId}" + logSuf)

val rigidBegin = EmptyTask() set(
    inputs  += (srcId, srcIm),
    outputs += (srcId, srcIm, dof6)
  ) source FileSource(dof6Template, dof6)

val rigidReg = ScalaTask(
  """
    | val src     = new java.io.File(workDir, imgPre + srcId + imgSuf)
    | val dof6    = new java.io.File(workDir, "transformation" + dofSuf)
    | val dof6Log = new java.io.File(workDir, "output" + logSuf)
    |
    | IRTK.ireg(refIm, src, None, dof6, Some(dof6Log),
    |   "Transformation model" -> "Rigid",
    |   "Background value"     -> 0
    | )
    |
  """.stripMargin) set(
    imports     += ("com.andreasschuh.repeat._", "com.andreasschuh.repeat.Workflow._"),
    usedClasses += (Workflow.getClass(), IRTK.getClass()),
    inputs      += srcId,
    inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
    outputs     += (srcId, srcIm),
    outputFiles += ("transformation.dof.gz", dof6),
    outputFiles += ("output.log", dof6Log)
  ) hook (
    CopyFileHook(dof6,    dof6Template),
    CopyFileHook(dof6Log, dof6LogTemplate)
  ) on parEnv

val rigidEnd = Capsule(EmptyTask() set (
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6)
  ))

val rigidCond = "!dof6.exists()"
val rigidMole = rigidBegin -- ((rigidReg -- rigidEnd) when rigidCond, rigidEnd when s"!($rigidCond)")

// Affine registration mole
val dof12Template    = Path.join(dofDir, "affine", refId + ",${srcId}" + dofSuf)
val dof12LogTemplate = Path.join(logDir, "affine", refId + ",${srcId}" + logSuf)

val affineBegin = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6, dof12)
  ) source FileSource(dof12Template, dof12)

val affineReg = ScalaTask(
  """
    | val src      = new java.io.File(workDir, imgPre + srcId + imgSuf)
    | val ini      = new java.io.File(workDir, "initial_guess"  + dofSuf)
    | val dof12    = new java.io.File(workDir, "transformation" + dofSuf)
    | val dof12Log = new java.io.File(workDir, "output" + logSuf)
    |
    | IRTK.ireg(refIm, src, Some(ini), dof12, Some(dof12Log),
    |   "Transformation model" -> "Affine",
    |   "Background value"     -> 0,
    |   "Padding value"        -> 0
    | )
    |
  """.stripMargin) set(
    imports     += ("com.andreasschuh.repeat._", "com.andreasschuh.repeat.Workflow._"),
    usedClasses += (Workflow.getClass(), IRTK.getClass()),
    inputs      += srcId,
    inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
    inputFiles  += (dof6, "initial_guess"      + dofSuf, symLnk),
    outputs     += (srcId, srcIm),
    outputFiles += ("transformation.dof.gz", dof12),
    outputFiles += ("output.log", dof12Log)
  ) hook (
    CopyFileHook(dof12,    dof12Template),
    CopyFileHook(dof12Log, dof12LogTemplate)
  ) on parEnv

val affineEnd = Capsule(EmptyTask() set (
    inputs  += (srcId, srcIm, dof12),
    outputs += (srcId, srcIm, dof12)
  ))

val affineCond = "dof12.lastModified() < dof6.lastModified()"
val affineMole = affineBegin -- ((affineReg -- affineEnd) when affineCond, affineEnd when s"!($affineCond)")

// Run spatial normalization pipeline for each input image
val exec = (forEachIm -< rigidMole) + (rigidEnd -- affineMole) start
exec.waitUntilEnded()
