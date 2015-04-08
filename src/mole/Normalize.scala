// OpenMOLE workflow for initial spatial normalization of images
import com.andreasschuh.repeat.Settings
import com.andreasschuh.repeat.Path
import com.andreasschuh.repeat.IRTK

// Environment on which to execute registration tasks
val env = LocalEnvironment(1)

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
val srcIdSampling = CSVSampling(imgCsv).set(columns += ("ID", srcId)).toSampling()
val forEachIm     = ExplorationTask(srcIdSampling + (srcIm in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf)))

// Rigid registration mole
val rigidOutputFile = FileSource(Path.join(dofDir, "rigid", refId + ",${srcId}" + dofSuf), dof6)

val rigidBegin = EmptyTask() set(
    inputs  += (srcId, srcIm),
    outputs += (srcId, srcIm, dof6)
  ) source rigidOutputFile

val rigidReg = ScalaTask(
  """IRTK.ireg(Settings.refIm, srcIm, None, dof6,
    |  "No. of threads"       -> 8,
    |  "Transformation model" -> "Rigid",
    |  "Background value"     -> 0
    |)
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Settings.getClass(), IRTK.getClass()),
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
  """IRTK.ireg(Settings.refIm, srcIm, Some(dof6), dof12,
    |  "No. of threads"       -> 8,
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

val affineEnd = Capsule(EmptyTask() set (
    inputs  += (srcId, srcIm, dof12),
    outputs += (srcId, srcIm, dof12)
  ))

val affineCond = "dof12.lastModified() < dof6.lastModified()"
val affineMole = affineBegin -- (((affineReg on env) -- affineEnd) when affineCond, affineEnd when s"!($affineCond)")

// Run spatial normalization pipeline for each input image
val exec = (forEachIm -< rigidMole) + (rigidEnd -- affineMole) start
exec.waitUntilEnded()
