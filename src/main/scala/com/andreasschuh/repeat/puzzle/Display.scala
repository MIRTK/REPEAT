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

package com.andreasschuh.repeat.puzzle

import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.Strain


/**
 * Instantiate capsule which writes a status message to STDOUT
 */
object Display {
  def apply(prefix: String, message: String, p: Prototype[_]*) = {
    val vals = "{" + p.map(p => p.name + "=${" + p.name + "}").mkString(", ") + "}"
    val task =
      ScalaTask(s"""println($prefix + s"$message for $vals") """) set (
        name    := s"Display.$prefix",
        imports += s"com.andreasschuh.repeat.core.Prefix.$prefix"
      )
    p.foreach(p => task.addInput(p))
    Strain(task)
  }
  def DONE(message: String, p: Prototype[_]*) = apply("DONE", message, p: _*)
  def INFO(message: String, p: Prototype[_]*) = apply("INFO", message, p: _*)
  def WARN(message: String, p: Prototype[_]*) = apply("WARN", message, p: _*)
  def SKIP(message: String, p: Prototype[_]*) = apply("SKIP", message, p: _*)
  def QSUB(message: String, p: Prototype[_]*) = apply("QSUB", message, p: _*)
}
