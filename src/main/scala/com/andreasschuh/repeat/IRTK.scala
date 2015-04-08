package com.andreasschuh.repeat

import scala.sys.process._
import java.io.File

/**
 * Interface to IRTK executables
 */
object IRTK {

  /// Directory containing executable binaries
  val binDir = Settings.irtkBinDir

  /// Version information
  def version: String = "[0-9]+(\\.[0-9]+)?(\\.[0-9]+)?".r.findFirstIn(s"$binDir/ireg -version".!!).getOrElse("1.0")

  /// Git commit SHA
  def revision: String = s"$binDir/ireg -revision".!!.trim

  /// Execute IRTK command
  protected def execute(cmd: Seq[String], errorOnReturnCode: Boolean = true): Int = {
    println(cmd.mkString("\n> \"", "\" \"", "\""))
    val returnCode = cmd.!
    if (errorOnReturnCode && returnCode != 0) throw new Exception(s"Error executing: ${cmd(0)} return code was not 0 but $returnCode")
    returnCode
  }

  /// Type of transformation file
  def dofType(dof: File): String = {
    if (!dof.exists()) throw new Exception(s"Tranformation does not exist: ${dof.getAbsolutePath}")
    Seq(s"$binDir/dofprint", dof.getAbsolutePath(), "-type").!!.trim
  }

  /// Whether given transformation is linear
  def isLinear(dof: File): Boolean = dofType(dof) match {
    case "irtkRigidTransformation" => true
    case "irtkAffineTransformation" => true
    case "irtkSimilarityTransformation" => true
    case _ => false
  }

  /// Whether given transformation is a FFD
  def isFFD(dof: File): Boolean = !isLinear(dof)

  /// Invert transformation
  def invert(dofIn: File, dofOut: File): Int = {
    if (!dofIn.exists()) throw new Exception(s"Input transformation does not exist: ${dofIn.getAbsolutePath}")
    dofOut.getAbsoluteFile().getParentFile().mkdirs()
    val binName = if (isLinear(dofIn)) "dofinvert" else "ffdinvert"
    execute(Seq(s"$binDir/$binName", dofIn.getAbsolutePath(), dofOut.getAbsolutePath()))
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
      execute(Seq(s"$binDir/dofcombine", dof2.getAbsolutePath, dof1.getAbsolutePath, dofOut.getAbsolutePath) ++ inv1 ++ inv2)
    } else {
      // TODO: Write inverse FFD to temporary file or even better add -invert1/-invert2 options to ffdcompose
      if (invert1) throw new Exception(s"ffdcompose does not support inversion of dof1 (${dofType(dof1)})")
      if (invert2) throw new Exception(s"ffdcompose does not support inversion of dof2 (${dofType(dof2)})")
      execute(Seq(s"$binDir/ffdcompose", dof1.getAbsolutePath, dof2.getAbsolutePath, dofOut.getAbsolutePath))
    }
  }

  /// Compute image transformation using ireg
  def ireg(target: File, source: File, dofin: Option[File], dofout: File, params: (String, Any)*): Int = {
    if (!target.exists()) throw new Exception(s"Target image does not exist: ${target.getAbsolutePath}")
    if (!source.exists()) throw new Exception(s"Source image does not exist: ${target.getAbsolutePath}")
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
      case (k, v) if k == "No. of threads" => Seq("-threads", v.toString)
      case (k, v) => Seq("-par", s"$k = $v")
      case _ => None
    }
    execute(Seq(s"$binDir/ireg", target.getAbsolutePath(), source.getAbsolutePath()) ++ din ++ dout ++ opts)
  }
}
