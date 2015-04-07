// OpenMOLE workflow for initial spatial normalization of images
import com.andreasschuh.repeat.Settings
import com.andreasschuh.repeat.IRTK

// Environment on which to execute registration tasks
val env = LocalEnvironment(4)

// Constants
val refId  = Settings.refId
val imgDir = Settings.imgIDir
val imgPre = Settings.imgPre
val imgSuf = Settings.imgSuf
val dofDir = Settings.dofDir
val dofSuf = Settings.dofSuf
val imgCsv = Settings.imgCsv

// Variables
val srcId = Val[Int]
val srcIm = Val[File]
val dof6  = Val[File]
val dof12 = Val[File]

// Exploration task which iterates the image IDs
val sampleId  = CSVSampling(imgCsv) set(columns += ("ID", srcId))
val forEachId = ExplorationTask(sampleId)

// Source objects used to inject variables into workflow
val _srcIm = FileSource(imgDir + "/" + imgPre + "${srcId}" + imgSuf, srcIm)
val _dof6  = FileSource(dofDir + "/rigid/"  + refId + ",${srcId}" + dofSuf, dof6)
val _dof12 = FileSource(dofDir + "/affine/" + refId + ",${srcId}" + dofSuf, dof12)

// Rigid registration mole
val rigidBegin = EmptyTask() set(
    inputs  += srcId,
    outputs += (srcId, dof6)
  ) source _dof6

val rigidIf = ScalaTask(
  """IRTK.ireg(Settings.refIm, srcIm, None, dof6,
    |  "Transformation model" -> "Rigid",
    |  "Background value"     -> 0
    |)
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Settings.getClass(), IRTK.getClass()),
    inputs      += (srcId, srcIm, dof6),
    outputs     += (srcId, srcIm, dof6)
  ) source _srcIm on env

val rigidElse = EmptyTask() set(
  inputs  += (srcId, srcIm, dof6),
  outputs += (srcId, srcIm, dof6)
  ) source _srcIm

val rigidEnd = ScalaTask(
  """val srcId = input.srcId.head
    |val srcIm = input.srcIm.head
    |val dof6  = input.dof6 .head
  """.stripMargin) set(
    inputs  += (srcId.toArray, srcIm.toArray, dof6.toArray),
    outputs += (srcId,         srcIm,         dof6)
  )

val rigidReg = rigidBegin -- (rigidIf when "!dof6.exists()", rigidElse when "dof6.exists()") -- rigidEnd

// Affine registration mole
val affineBegin = EmptyTask() set(
    inputs  += (srcId, dof6),
    outputs += (srcId, dof6, dof12)
  ) source _dof12

val affineIf = ScalaTask(
  """IRTK.ireg(Settings.refIm, srcIm, Some(dof6), dof12,
    |  "Transformation model" -> "Affine",
    |  "Background value"     -> 0,
    |  "Padding value"        -> 0
    |)
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Settings.getClass(), IRTK.getClass()),
    inputs      += (srcId, srcIm, dof6, dof12),
    outputs     += (srcId, srcIm,       dof12)
  ) source _srcIm on env

val affineElse = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6, dof12),
    outputs += (srcId, srcIm,       dof12)
  ) source _srcIm

val affineEnd = ScalaTask(
  """val srcId = input.srcId.head
    |val srcIm = input.srcIm.head
    |val dof12 = input.dof12.head
  """.stripMargin) set(
    inputs  += (srcId.toArray, srcIm.toArray, dof12.toArray),
    outputs += (srcId,         srcIm,         dof12)
  )

val affineReg = affineBegin -- (affineIf when "!dof12.exists()", affineElse when "dof12.exists()") -- affineEnd

// Run spatial normalization pipeline for each input image
val mole = forEachId -< rigidReg -- affineReg start
mole.waitUntilEnded()
