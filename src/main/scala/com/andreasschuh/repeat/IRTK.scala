package com.andreasschuh.repeat

import scala.sys.process._
import java.io.File

/**
 * Interface to IRTK executables
 */
object IRTK extends Configurable("irtk") {

  /// Directory containing executable binaries
  val binDir = {
    val dir = getFileProperty("bindir")
    if (!dir.exists()) throw new Exception(s"IRTK bin directory does not exist: $dir")
    val ireg = new File(dir, "ireg")
    if (!ireg.exists()) throw new Exception(s"Invalid IRTK version, missing ireg executable: $ireg")
    dir
  }

  /// Maximum number of threads to be used by each command
  val nThreads = getIntProperty("threads")

  /// Version information
  def version: String = "[0-9]+(\\.[0-9]+)?(\\.[0-9]+)?".r.findFirstIn(s"$binDir/ireg -version".!!).getOrElse("1.0")

  /// Git commit SHA
  def revision: String = s"$binDir/ireg -revision".!!.trim

  /// Execute IRTK command
  protected def execute(command: String, args: Seq[String], log: Option[File] = None, errorOnReturnCode: Boolean = true): Int = {
    val cmd = Seq[String](Path.join(binDir, command)) ++ args
    val cmdString = cmd.mkString("> \"", "\" \"", "\"\n")
    print('\n')
    val returnCode = log match {
      case Some(file) => {
        val logger = new TaskLogger(file)
        if (!logger.tee) println(cmdString)
        logger.out(cmdString)
        cmd ! logger
      }
      case None => {
        println(cmdString)
        cmd.!
      }
    }
    if (errorOnReturnCode && returnCode != 0) {
      throw new Exception(s"Error executing: ${cmd(0)} return code was not 0 but $returnCode")
    }
    returnCode
  }

  /// Type of transformation file
  def dofType(dof: File): String = {
    if (!dof.exists()) throw new Exception(s"Tranformation does not exist: ${dof.getAbsolutePath}")
    Seq[String](Path.join(binDir, "dofprint"), dof.getAbsolutePath(), "-type").!!.trim
  }

  /// Whether given transformation is linear
  def isLinear(dof: File): Boolean = dofType(dof) match {
    case "irtkRigidTransformation" | "irtkAffineTransformation" | "irtkSimilarityTransformation" => true
    case _ => false
  }

  /// Whether given transformation is a FFD
  def isFFD(dof: File): Boolean = !isLinear(dof)

  /// Invert transformation
  def invert(dofIn: File, dofOut: File): Int = {
    if (!dofIn.exists()) throw new Exception(s"Input transformation does not exist: ${dofIn.getAbsolutePath}")
    dofOut.getAbsoluteFile().getParentFile().mkdirs()
    execute(if (isLinear(dofIn)) "dofinvert" else "ffdinvert", Seq(dofIn.getAbsolutePath(), dofOut.getAbsolutePath()))
  }

  /// Compose transformations: (dof2 o dof1)
  def compose(dof1: File, dof2: File, dofOut: File, invert1: Boolean = false, invert2: Boolean = false): Int = {
    if (!dof1.exists()) throw new Exception(s"Input dof1 does not exist: ${dof1.getAbsolutePath}")
    if (!dof2.exists()) throw new Exception(s"Input dof2 does not exist: ${dof2.getAbsolutePath}")
    dofOut.getAbsoluteFile().getParentFile().mkdirs()
    if (isLinear(dof1) && isLinear(dof2)) {
      // Note: dof1 and dof2 arguments are swapped!
      val inv1 = if (invert1) Seq("-invert2") else Seq()
      val inv2 = if (invert2) Seq("-invert1") else Seq()
      execute("dofcombine", Seq(dof2.getAbsolutePath, dof1.getAbsolutePath, dofOut.getAbsolutePath) ++ inv1 ++ inv2)
    } else {
      // TODO: Write inverse FFD to temporary file or even better add -invert1/-invert2 options to ffdcompose
      if (invert1) throw new Exception(s"ffdcompose does not support inversion of dof1 (${dofType(dof1)})")
      if (invert2) throw new Exception(s"ffdcompose does not support inversion of dof2 (${dofType(dof2)})")
      execute("ffdcompose", Seq(dof1.getAbsolutePath, dof2.getAbsolutePath, dofOut.getAbsolutePath))
    }
  }

  /// Compute image transformation using ireg
  def ireg(target: File, source: File, dofin: Option[File], dofout: File, log: Option[File], params: (String, Any)*): Int = {
    if (!target.exists()) throw new Exception(s"Target image does not exist: ${target.getAbsolutePath}")
    if (!source.exists()) throw new Exception(s"Source image does not exist: ${source.getAbsolutePath}")
    dofout.getAbsoluteFile().getParentFile().mkdirs()
    val din = dofin match {
      case Some(file) => {
        if (!file.exists()) throw new Exception(s"Initial transformation does not exist: ${file.getAbsolutePath}")
        Seq("-dofin", file.getAbsolutePath())
      }
      case None => Seq()
    }
    val dout = Seq("-dofout", dofout.getAbsolutePath())
    val opts = params flatMap {
      case (k, v) if k == "Verbosity" => Seq("-verbose", v.toString)
      case (k, v) if k == "No. of threads" => Seq("-threads", v.toString)
      case (k, v) => Seq("-par", s"$k = $v")
      case _ => None
    }
    execute("ireg", Seq(target.getAbsolutePath(), source.getAbsolutePath(), "-threads", nThreads.toString) ++ din ++ dout ++ opts, log)
  }
}
