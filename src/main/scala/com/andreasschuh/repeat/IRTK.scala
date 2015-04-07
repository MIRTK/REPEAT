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
  protected def execute(cmd: Seq[String]): Int = {
    println(cmd.mkString("\n> \"", "\" \"", "\""))
    cmd.!
  }

  /// Invert transformation
  def dofinvert(dofin: File, dofout: File): Int = {
    dofout.getAbsoluteFile().getParentFile().mkdirs()
    execute(Seq(s"$binDir/dofinvert", dofin.getAbsolutePath(), dofout.getAbsolutePath()))
  }

  /// Register images using ireg
  def ireg(target: File, source: File, dofin: Option[File], dofout: File, params: (String, Any)*): Int = {
    dofout.getAbsoluteFile().getParentFile().mkdirs()
    val din = dofin match {
      case Some(file) => Seq("-dofin", file.getAbsolutePath())
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
