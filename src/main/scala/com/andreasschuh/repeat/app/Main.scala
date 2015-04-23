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

package com.andreasschuh.repeat.app

import com.andreasschuh.repeat.core._
import java.io.{ ByteArrayInputStream, File }
import scala.sys.process._


/**
 * REPEAT application which executes the OpenMOLE workflows
 */
object Main extends App {

  /** File path of the REPEAT OpenMOLE plugin .jar file itself */
  val repeatPluginJar = new File(Main.getClass.getProtectionDomain.getCodeSource.getLocation.toURI)

  /** OpenMOLE script running the complete registration evaluation workflow */
  val script =
    """
      | import com.andreasschuh.repeat.workflow._
      | import com.andreasschuh.repeat.core.Registration
      | val reg = Registration(args(0))
      | PreRegistration ()   .start.waitUntilEnded
      | RunRegistration (reg).start.waitUntilEnded
      | EvalRegistration(reg).start.waitUntilEnded
    """.stripMargin

  // Execute workflow script in OpenMOLE console
  val istream = new ByteArrayInputStream(script.getBytes("UTF-8"))
  val logger  = new Logger
  (Cmd("openmole", "-c", "-p", repeatPluginJar.getAbsolutePath, "--") ++ args) #< istream ! logger
}
