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

val rigidTask = ScalaTask(
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

val rigidEnd = Capsule(EmptyTask() set(
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6)
  ))

val rigidIf  = (rigidTask on env) -- Slot(rigidEnd)
val rigidReg = rigidBegin -- (rigidIf when "!dof6.exists()", Slot(rigidEnd) when "dof6.exists()")

// Affine registration mole
val affineOutputFile = FileSource(dofDir + "/affine/" + refId + ",${srcId}" + dofSuf, dof12)

val affineBegin = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6, dof12)
  ) source affineOutputFile

val affineTask = ScalaTask(
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

val affineEnd = Capsule(EmptyTask() set(
    inputs  += (srcId, srcIm, dof12),
    outputs += (srcId, srcIm, dof12)
  ))

val affineIf  = (affineTask on env) -- Slot(affineEnd)
val affineReg = rigidEnd -- affineBegin -- (affineIf when "!dof12.exists()", Slot(affineEnd) when "dof12.exists()")

// Run spatial normalization pipeline for each input image
val mole = forEachIm -- rigidReg + affineReg start
mole.waitUntilEnded()
