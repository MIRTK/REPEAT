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
import java.io.FileWriter
import scala.sys.process.ProcessLogger


/**
 * File process logger
 */
class TaskLogger(log: File) extends Configurable("workspace.logs") with ProcessLogger {

  private var t = Array.fill(3)(.0)

  /** Reset time measurements */
  def resetTime(): Unit = t = Array.fill(3)(0)

  /** Whether any time measurements were recorded */
  def hasTime: Boolean = t.sum != 0

  /** Get runtime measurements in seconds: user, system, total, real */
  def time = Array(t(0), t(1), (100.0 * (t(0) + t(1))).toInt.toDouble / 100.0, t(2))

  /** File writer object */
  protected val writer = {
    val logFile = log.getAbsoluteFile
    logFile.getParentFile.mkdirs()
    new FileWriter(logFile, WorkSpace.appLog)
  }

  /** Close log file */
  def close() = writer.close()

  /** Whether to flush buffers after each line read from STDOUT (STDERR is always written immediately) */
  val flush = getBooleanProperty("flush")

  /** Whether to also print output to STDOUT */
  val tee = getBooleanProperty("tee")

  /** Write line of process' STDOUT */
  def out(s: => String): Unit = {
    writer.write(s)
    writer.write('\n')
    if (flush) writer.flush()
    if (tee) Console.out.println(s)
  }

  /** Write line of process' STDERR */
  def err(s: => String): Unit = {
    writer.write(s)
    writer.write('\n')
    writer.flush()
    if (tee) Console.err.println(s)
    """(user|sys|real)\s+([0-9]+\.[0-9]+)""".r.findAllMatchIn(s).foreach(m =>
      m.group(1) match {
        case "user" => t(0) = m.group(2).toDouble
        case "sys"  => t(1) = m.group(2).toDouble
        case "real" => t(2) = m.group(2).toDouble
      }
    )
  }

  /** Wrap process execution and close file when finished */
  def buffer[T](f: => T): T = f
}
