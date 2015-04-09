package com.andreasschuh.repeat

import java.io.File

/**
 * SLURM related settings
 */
object SLURM extends Configurable("slurm") {

  /// Hostname of SLURM head node
  val host = getStringProperty("host")

  /// SLURM user name
  val user = getStringProperty("user")

  /// Name of queue for short running jobs (< 1hr)
  val queueShort = getStringProperty("queue.short")

  /// Name of queue for long running jobs (>= 1hr)
  val queueLong = getStringProperty("queue.long")

  /// Authentication token for SLURM head node, e.g., name of SSH key file or password
  val auth = getStringProperty("auth")

  /// SSH key file if specified as authentication token
  val sshKey: Option[File] = auth match {
    case "id_dsa" | "id_rsa" => {
      val sshDir = new File(System.getProperty("user.home"), ".ssh")
      if (sshDir.exists())
        Some[File](new File(sshDir, auth))
      else
        None
    }
    case _ => None
  }
}
