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

package com.andreasschuh.repeat

import java.io.{ByteArrayInputStream, File}
import com.andreasschuh.repeat.core._
import scala.sys.process._


/**
 * REPEAT application which executes the OpenMOLE workflows
 */
object App extends scala.App {

  /** OpenMOLE console output logger */
  private class Logger extends ProcessLogger {

    /**
     * Ignore all output to STDOUT until OpenMOLE console is started up
     * (in particular, don't print ASCII art OpenMOLE splash screen)
     */
    protected var startedUp: Boolean = false

    /** Process STDOUT line */
    def out(s: => String): Unit = {
      startedUp = startedUp || s.startsWith("OpenMOLE>")
      val ignore = !startedUp || s.contains("feature warning") ||
        "OpenMOLE>|import |[a-zA-Z_][a-zA-Z0-9_]*: ".r.findPrefixOf(s) != None
      if (!ignore) println(s)
    }

    /** Process STDERR line */
    def err(s: => String): Unit = {
      println(s)
    }

    /** Wrap process execution */
    def buffer[T](f: => T): T = f
  }

  // File path of the REPEAT OpenMOLE plugin .jar file itself
  private def repeatPluginJar = new File(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).getAbsolutePath

  // OpenMOLE script running the complete registration evaluation workflow
  private def script =
    """
      | import com.andreasschuh.repeat
      | repeat.init()
      | repeat.evaluate(reg = args(0))
    """.stripMargin

  // Check arguments
  if (args.length != 1) {
    Console.err.println(
      """
        | Required arguments:
        |   reg   Name of registration whose performance should be assessed.
        |         Must correspond to a "registrations" entry in the repeat.conf file.
      """.stripMargin.trim)
    System.exit(1)
  }
  try {
    new Registration(args(0))
  } catch {
    case e: Exception => {
      Console.err.println("Unknown registration: " + args(0))
      System.exit(1)
    }
  }

  // Execute workflow script in OpenMOLE console
  private val stream = new ByteArrayInputStream(script.getBytes("UTF-8"))
  private val logger = new Logger
  (Cmd("openmole", "-mem", "4G", "-c", "-p", repeatPluginJar, "--") ++ args) #< stream ! logger
}
