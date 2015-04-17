package com.andreasschuh.repeat.core

import java.io.File

/**
 * SLURM related settings
 */
object SLURM extends Configurable("slurm") {

  /// Hostname of SLURM head node
  val host = getStringProperty("host")

  /// SLURM user name
  val user = getStringProperty("user")

  /// Name of specified queue (e.g., "long" or "short")
  def queue(name: String): String = getStringProperty(s"queue.$name")

  /// Authentication token for SLURM head node, e.g., name of SSH key file or password
  val auth = getStringProperty("auth")

  /// SSH key file if specified as authentication token
  val sshKey: Option[File] = auth match {
    case "id_dsa" | "id_rsa" =>
      val sshDir = new File(System.getProperty("user.home"), ".ssh")
      if (sshDir.exists()) Some[File](new File(sshDir, auth))
      else None
    case _ => None
  }
}
