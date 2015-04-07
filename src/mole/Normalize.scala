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
val sampleId  = CSVSampling(imgCsv) set ( columns += ("ID", srcId) )
val forEachId = ExplorationTask(sampleId)

// Source objects used to inject variables into workflow
val _srcIm = FileSource(imgDir + "/" + imgPre + "${srcId}" + imgSuf, srcIm)
val _dof6  = FileSource(dofDir + "/rigid/"  + refId + ",${srcId}" + dofSuf, dof6)
val _dof12 = FileSource(dofDir + "/affine/" + refId + ",${srcId}" + dofSuf, dof12)

// Rigid registration mole
val rigidDof = EmptyTask() set (
    inputs  += srcId,
    outputs += (srcId, dof6)
  )

val rigidNOP = EmptyTask() set (
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6)
  )

val rigidDemux = ScalaTask("val dof6 = input.dof6.head") set (
    inputs  += (srcId, srcIm, dof6.toArray),
    outputs += (srcId, srcIm, dof6)
  )

val rigidReg = ScalaTask(
  """if (!dof6.exists()) {
    |  IRTK.ireg(Settings.refIm, srcIm, None, dof6,
    |    "Transformation model" -> "Rigid",
    |    "Background value"     -> 0
    |  )
    |}
  """.stripMargin) set (
    imports += "com.andreasschuh.repeat._",
    usedClasses += (Settings.getClass(), IRTK.getClass()),
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6)
  )

//val rigid = (rigidDof source _dof6) -- ((rigidReg source _srcIm on env when "!dof6.exists()"),
//                                        (rigidNOP source _srcIm        when " dof6.exists()")) -- rigidDemux
val rigid = rigidReg source (_srcIm, _dof6) on env

// Affine registration mole
val affineDof = EmptyTask() set (
    inputs  += srcId,
    outputs += (srcId, dof12)
  )

val affineNOP = EmptyTask() set (
    inputs  += (srcId, srcIm, dof6, dof12),
    outputs += (srcId, srcIm, dof12)
  )

val affineDemux = ScalaTask("val dof12 = input.dof12.head") set (
    inputs  += (srcId, srcIm, dof12.toArray),
    outputs += (srcId, srcIm, dof12)
  )

val affineReg = ScalaTask(
  """if (!dof12.exists()) {
    |  IRTK.ireg(Settings.refIm, srcIm, Some(dof6), dof12,
    |    "Transformation model" -> "Affine",
    |    "Background value"     -> 0,
    |    "Padding value"        -> 0
    |  )
    |}
  """.stripMargin) set (
    imports += "com.andreasschuh.repeat._",
    usedClasses += (Settings.getClass(), IRTK.getClass()),
    inputs  += (srcId, srcIm, dof6, dof12),
    outputs += (srcId, srcIm, dof12)
  )

//val affine = (affineDof source _dof12) -- ((affineReg on env when "!dof12.exists()"),
//                                           (affineNOP        when " dof12.exists()")) -- affineDemux
val affine = affineReg source _dof12 on env

// Run spatial normalization pipeline for each input image
val mole = forEachId -< rigid -- affine start
mole.waitUntilEnded()
