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

  /** File writer object */
  protected val writer = {
    val logFile = log.getAbsoluteFile
    logFile.getParentFile.mkdirs()
    new FileWriter(logFile, getBooleanProperty("append"))
  }

  /** Whether to flush buffers after each line read from STDOUT (STDERR is always written immediately) */
  val flush = getBooleanProperty("flush")

  /** Whether to also print output to STDOUT */
  val tee = getBooleanProperty("tee")

  /** Write line of process' STDOUT */
  def out(s: => String): Unit = {
    writer.write(s)
    writer.write('\n')
    if (flush) writer.flush()
    if (tee) println(s)
  }

  /** Write line of process' STDERR */
  def err(s: => String): Unit = {
    writer.write(s)
    writer.write('\n')
    writer.flush()
    if (tee) println(s)
  }

  /** Wrap process execution and close file when finished */
  def buffer[T](f: => T): T = {
    val returnValue: T = f
    writer.close()
    returnValue
  }
}
