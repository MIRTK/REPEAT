// =============================================================================
// Project: Registration Performance Assessment Tool (REPEAT)
// Module:  OpenMOLE script for initial spatial normalization to MNI space
//
// Copyright (c) 2015, Andreas Schuh.
// See LICENSE file for license information.
// =============================================================================

import com.andreasschuh.repeat._

// Environment on which to execute registration tasks
val env = Workflow.env

// Constants
val refId  = Workflow.refId
val imgDir = Workflow.imgIDir
val imgPre = Workflow.imgPre
val imgSuf = Workflow.imgSuf
val dofDir = Workflow.dofDir
val dofSuf = Workflow.dofSuf
val imgCsv = Workflow.imgCsv

// Variables
val srcId = Val[Int]
val srcIm = Val[File]
val dof6  = Val[File]
val dof12 = Val[File]

// Exploration task which iterates the image IDs and file paths
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("ID", srcId))
val forEachIm     = ExplorationTask(srcIdSampling + (srcIm in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)))

// Rigid registration mole
val rigidOutputFile = FileSource(Path.join(dofDir, "rigid", refId + ",${srcId}" + dofSuf), dof6)

val rigidBegin = EmptyTask() set(
    inputs  += (srcId, srcIm),
    outputs += (srcId, srcIm, dof6)
  ) source rigidOutputFile

val rigidReg = ScalaTask(
  """IRTK.ireg(Workflow.refIm, srcIm, None, dof6, None,
    |  "No. of threads"       -> 8,
    |  "Transformation model" -> "Rigid",
    |  "Background value"     -> 0
    |)
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Workflow.getClass(), IRTK.getClass()),
    inputs      += (srcId, srcIm, dof6),
    outputs     += (srcId, srcIm, dof6)
  )

val rigidEnd = Capsule(EmptyTask() set (
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6)
  ))

val rigidCond = "!dof6.exists()"
val rigidMole = rigidBegin -- (((rigidReg on env) -- rigidEnd) when rigidCond, rigidEnd when s"!($rigidCond)")

// Affine registration mole
val affineOutputFile = FileSource(Path.join(dofDir, "affine", refId + ",${srcId}" + dofSuf), dof12)

val affineBegin = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6, dof12)
  ) source affineOutputFile

val affineReg = ScalaTask(
  """IRTK.ireg(Workflow.refIm, srcIm, Some(dof6), dof12, None,
    |  "No. of threads"       -> 8,
    |  "Transformation model" -> "Affine",
    |  "Background value"     -> 0,
    |  "Padding value"        -> 0
    |)
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Workflow.getClass(), IRTK.getClass()),
    inputs      += (srcId, srcIm, dof6, dof12),
    outputs     += (srcId, srcIm,       dof12)
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
