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
class TaskLogger(log: Option[File] = None) extends Configurable("workspace.logs") with ProcessLogger {

  private var t = Array.fill(3)(.0)

  /** Reset time measurements */
  def resetTime(): Unit = t = Array.fill(3)(0)

  /** Whether any time measurements were recorded */
  def hasTime: Boolean = t.sum != 0

  /** Whether no time measurements were recorded */
  def noTime: Boolean = !hasTime

  /** Get runtime measurements in seconds: user, system, real */
  def time = t

  /** File writer object */
  protected val writer = log match {
    case Some(f) =>
      val logFile = f.getAbsoluteFile
      logFile.getParentFile.mkdirs()
      Some(new FileWriter(logFile, WorkSpace.appLog))
    case None => None
  }

  /** Close log file */
  def close() = writer.foreach(_.close())

  /** Whether to flush buffers after each line read from STDOUT (STDERR is always written immediately) */
  val flush = getBooleanProperty("flush")

  /** Whether to (also) print output to standard output streams */
  val tee = (writer == None) || getBooleanProperty("tee")

  /** Write line of process' STDOUT */
  def out(s: => String): Unit = {
    writer.foreach(log => {
      log.write(s)
      log.write('\n')
      if (flush) log.flush()
    })
    if (tee) Console.out.println(s)
  }

  /** Write line of process' STDERR */
  def err(s: => String): Unit = {
    writer.foreach(log => {
      log.write(s)
      log.write('\n')
      log.flush()
    })
    if (tee) Console.err.println(s)
    """(user|sys|real)\s+([0-9]+\.[0-9]+)""".r.findAllMatchIn(s).foreach(m =>
      m.group(1) match {
        case "user" => t(0) = m.group(2).toDouble
        case "sys"  => t(1) = m.group(2).toDouble
        case "real" => t(2) = m.group(2).toDouble
      }
    )
  }

  /** Wrap process execution */
  def buffer[T](f: => T): T = f

  /** Print content of log file to STDERR */
  def printToErr() = if (log != None) {
    try { writer.foreach(_.flush()) } catch { case _: java.io.IOException => }
    val logFile = log.get.getAbsoluteFile
    scala.io.Source.fromFile(logFile).getLines().foreach(Console.err.println)
  }
}
