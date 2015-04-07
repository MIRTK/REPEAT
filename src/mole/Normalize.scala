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

// Exploration task which iterates the image IDs and file paths
val sampleId  = CSVSampling(imgCsv) set(columns += ("ID", srcId))
val srcImFile = FileSource(imgDir + "/" + imgPre + "${srcId}" + imgSuf, srcIm)
val forEachIm = ExplorationTask(sampleId) -< (EmptyTask() set(inputs += srcId, outputs += (srcId, srcIm)) source srcImFile)

// Rigid registration mole
val rigidOutputFile = FileSource(dofDir + "/rigid/"  + refId + ",${srcId}" + dofSuf, dof6)

val rigidBegin = EmptyTask() set(
    inputs  += (srcId, srcIm),
    outputs += (srcId, srcIm, dof6)
  ) source rigidOutputFile

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
  )

val rigidElse = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6)
  )

val rigidEnd = ScalaTask(
  """val srcId = input.srcId.head
    |val srcIm = input.srcIm.head
    |val dof6  = input.dof6 .head
  """.stripMargin) set(
    inputs  += (srcId.toArray, srcIm.toArray, dof6.toArray),
    outputs += (srcId,         srcIm,         dof6)
  )

val rigidReg = rigidBegin -- (rigidIf on env when "!dof6.exists()", rigidElse when "dof6.exists()") -- rigidEnd

// Affine registration mole
val affineOutputFile = FileSource(dofDir + "/affine/" + refId + ",${srcId}" + dofSuf, dof12)

val affineBegin = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6, dof12)
  ) source affineOutputFile

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
  )

val affineElse = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6, dof12),
    outputs += (srcId, srcIm,       dof12)
  )

val affineEnd = ScalaTask(
  """val srcId = input.srcId.head
    |val srcIm = input.srcIm.head
    |val dof12 = input.dof12.head
  """.stripMargin) set(
    inputs  += (srcId.toArray, srcIm.toArray, dof12.toArray),
    outputs += (srcId,         srcIm,         dof12)
  )

val affineReg = affineBegin -- (affineIf on env when "!dof12.exists()", affineElse when "dof12.exists()") -- affineEnd

// Run spatial normalization pipeline for each input image
val mole = forEachIm -- rigidReg -- affineReg start
mole.waitUntilEnded()
