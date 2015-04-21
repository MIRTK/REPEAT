/*
 * Registration Performance Assessment Tool (REPEAT)
 *
 * Copyright (C) 2015  Andreas Schuh
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
 */

package com.andreasschuh.repeat.core

import java.io.File


/**
 * SLURM related settings
 */
object SLURM extends Configurable("slurm") {

  /** Hostname of SLURM head node */
  val host = getStringProperty("host")

  /** SLURM user name */
  val user = getStringProperty("user")

  /** Name of specified queue (e.g., "long" or "short") */
  def queue(name: String): String = getStringProperty(s"queue.$name")

  /** Authentication token for SLURM head node, e.g., name of SSH key file or password */
  val auth = getStringProperty("auth")

  /** SSH key file if specified as authentication token */
  val sshKey: Option[File] = auth match {
    case "id_dsa" | "id_rsa" =>
      val sshDir = new File(System.getProperty("user.home"), ".ssh")
      if (sshDir.exists()) Some[File](new File(sshDir, auth))
      else None
    case _ => None
  }
}
