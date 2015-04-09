// =============================================================================
// Project: Registration Performance Assessment Tool (REPEAT)
// Module:  OpenMOLE script for initial spatial normalization to MNI space
//
// Copyright (c) 2015, Andreas Schuh.
// See LICENSE file for license information.
// =============================================================================

import com.andreasschuh.repeat._

// Environment on which to execute registrations
val env = Workflow.env

// Constants
val refId  = Workflow.refId
val imgCsv = Workflow.imgCsv
val imgDir = Workflow.imgIDir
val imgPre = Workflow.imgPre
val imgSuf = Workflow.imgSuf
val dofSuf = Workflow.dofSuf
val dofDir = Workflow.dofDir
val logDir = Workflow.logDir

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
val dof6LogTemplate = Path.join(logDir, "rigid", refId + ",${srcId}.log")

val rigidBegin = EmptyTask() set(
    inputs  += (srcId, srcIm),
    outputs += (srcId, srcIm, dof6)
  ) source FileSource(dof6Template, dof6)

val rigidReg = ScalaTask(
  """
    | val dof6    = newFile
    | val dof6Log = newFile
    |
    | IRTK.ireg(Workflow.refIm, srcIm, None, dof6, Some(dof6Log),
    |   "No. of threads"       -> 8,
    |   "Transformation model" -> "Rigid",
    |   "Background value"     -> 0
    | )
    |
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Workflow.getClass(), IRTK.getClass()),
    inputs      += (srcId, srcIm),
    outputs     += (srcId, srcIm, dof6, dof6Log)
  ) hook (
    CopyFileHook(dof6,    dof6Template),
    CopyFileHook(dof6Log, dof6LogTemplate)
  )

val rigidEnd = Capsule(EmptyTask() set (
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6)
  ))

val rigidCond = "!dof6.exists()"
val rigidMole = rigidBegin -- (((rigidReg on env) -- rigidEnd) when rigidCond, rigidEnd when s"!($rigidCond)")

// Affine registration mole
val dof12Template    = Path.join(dofDir, "affine", refId + ",${srcId}" + dofSuf)
val dof12LogTemplate = Path.join(logDir, "affine", refId + ",${srcId}.log")

val affineBegin = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6, dof12)
  ) source FileSource(dof12Template, dof12)

val affineReg = ScalaTask(
  """
    | val dof12    = newFile
    | val dof12Log = newFile
    |
    | IRTK.ireg(Workflow.refIm, srcIm, Some(dof6), dof12, Some(dof12Log),
    |   "No. of threads"       -> 8,
    |   "Transformation model" -> "Affine",
    |   "Background value"     -> 0,
    |   "Padding value"        -> 0
    | )
    |
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Workflow.getClass(), IRTK.getClass()),
    inputs      += (srcId, srcIm, dof6),
    outputs     += (srcId, srcIm, dof12, dof12Log)
  ) hook (
    CopyFileHook(dof12,    dof12Template),
    CopyFileHook(dof12Log, dof12LogTemplate)
  )

val affineEnd = Capsule(EmptyTask() set (
    inputs  += (srcId, srcIm, dof12),
    outputs += (srcId, srcIm, dof12)
  ))

val affineCond = "dof12.lastModified() < dof6.lastModified()"
val affineMole = affineBegin -- (((affineReg on env) -- affineEnd) when affineCond, affineEnd when s"!($affineCond)")

// Run spatial normalization pipeline for each input image
val exec = (forEachIm -< rigidMole) + (rigidEnd -- affineMole) start
exec.waitUntilEnded()
