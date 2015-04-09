// OpenMOLE workflow for pairwise affine registration of images
import com.andreasschuh.repeat._

// Environment on which to execute registration tasks
val env = LocalEnvironment(1)

// Constants
val refId  = Workflow.refId
val imgDir = Workflow.imgIDir
val imgPre = Workflow.imgPre
val imgSuf = Workflow.imgSuf
val dofDir = Workflow.dofDir
val dofSuf = Workflow.dofSuf
val imgCsv = Workflow.imgCsv

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
val compBegin = EmptyTask() set (
    inputs  += (tgtId, tgtIm, tgtDof, srcId, srcIm, srcDof),
    outputs += (tgtId, tgtIm, tgtDof, srcId, srcIm, srcDof, iniDof)
  ) source FileSource(Path.join(dofDir, "initial", "${tgtId},${srcId}" + dofSuf), iniDof)

val compTask = ScalaTask("IRTK.compose(tgtDof, srcDof, iniDof, true, false)") set (
    imports     += "com.andreasschuh.repeat.IRTK",
    usedClasses += IRTK.getClass(),
    inputs      += (tgtId, tgtIm, tgtDof, srcId, srcIm, srcDof, iniDof),
    outputs     += (tgtId, tgtIm,         srcId, srcIm,         iniDof)
  )

val compEnd = Capsule(EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, iniDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, iniDof)
  ))

val compCond = "iniDof.lastModified() < tgtDof.lastModified() || iniDof.lastModified() < srcDof.lastModified()"
val compMole = compBegin -- (((compTask on env) -- compEnd) when compCond, compEnd when s"!($compCond)")

// Affine registration mole
val affineBegin = EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, iniDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, iniDof, outDof)
  ) source FileSource(Path.join(dofDir, "affine", "${tgtId},${srcId}" + dofSuf), outDof)

val affineTask = ScalaTask(
  """IRTK.ireg(tgtIm, srcIm, Some(iniDof), outDof, None,
    |  "Transformation model"     -> "Affine",
    |  "No. of threads"           -> 8,
    |  "No. of resolution levels" -> 2,
    |  "Background value"         -> 0,
    |  "Padding value"            -> 0
    |)
  """.stripMargin) set (
    imports     += "com.andreasschuh.repeat.IRTK",
    usedClasses += IRTK.getClass(),
    inputs      += (tgtId, tgtIm, srcId, srcIm, iniDof, outDof),
    outputs     += (tgtId, tgtIm, srcId, srcIm,         outDof)
  )

val affineEnd = Capsule(EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, outDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, outDof)
  ))

val affineCond = "outDof.lastModified() < iniDof.lastModified()"
val affineMole = affineBegin -- (((affineTask on env) -- affineEnd) when affineCond, affineEnd when s"!($affineCond)")

// Invert transformation
val invBegin = EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, outDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, outDof, invDof)
  ) source FileSource(Path.join(dofDir, "affine", "${srcId},${tgtId}" + dofSuf), invDof)

val invTask = ScalaTask("IRTK.invert(outDof, invDof)") set (
    imports     += "com.andreasschuh.repeat.IRTK",
    usedClasses += IRTK.getClass(),
    inputs  += (tgtId, tgtIm, srcId, srcIm, outDof, invDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, outDof, invDof)
  )

val invEnd = Capsule(EmptyTask() set (
    inputs  += (tgtId, tgtIm, srcId, srcIm, outDof, invDof),
    outputs += (tgtId, tgtIm, srcId, srcIm, outDof, invDof)
  ))

val invCond = "invDof.lastModified() < outDof.lastModified()"
val invMole = invBegin -- (((invTask on env) -- invEnd) when invCond, invEnd when s"!($invCond)")

// Run affine registration pipeline for each pair of images
val exec = (forEachTuple -< compMole) + (compEnd -- affineMole) + (affineEnd -- invMole) start
exec.waitUntilEnded()
