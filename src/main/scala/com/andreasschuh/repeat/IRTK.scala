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

  /// Invert transformation
  def dofinvert(dofin: File, dofout: File): Int = {
    println(Seq(s"$binDir/dofinvert", dofin.getAbsolutePath, dofout.getAbsolutePath))
    0
    dofout.getAbsoluteFile().getParentFile().mkdirs()
  }

  /// Register images using ireg
  def ireg(target: File, source: File, dofin: Option[File], dofout: File, params: (String, Any)*): Int = {
    dofout.getAbsoluteFile().getParentFile().mkdirs()
    val din = dofin match {
      case Some(file) => Seq("-dofin", file.getAbsolutePath)
      case None => Seq()
    }
    val cfg = params flatMap {
      case (k, v) => Seq("-par", s"$k = $v")
      case _ => None
    }
    val cmd = Seq(s"$binDir/ireg", target.getAbsolutePath, source.getAbsolutePath, "-dofout", dofout.getAbsolutePath) ++ din ++ cfg
    println(cmd)
    0
  }
}
