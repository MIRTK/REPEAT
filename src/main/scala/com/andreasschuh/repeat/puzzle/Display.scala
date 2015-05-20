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
import org.openmole.plugin.task.scala._


/**
 * Instantiate capsule which writes a status message to STDOUT
 */
object Display {
  def apply(prefix: String, message: String) =
    Capsule(
      ScalaTask(s"""println(s"$${$prefix}$message") """) set (
        imports += s"com.andreasschuh.repeat.core.Prefix.$prefix"
      ),
      strainer = true
    )
  def DONE(message: String) = apply("DONE", message)
  def INFO(message: String) = apply("INFO", message)
  def WARN(message: String) = apply("WARN", message)
  def SKIP(message: String) = apply("SKIP", message)
  def QSUB(message: String) = apply("QSUB", message)
}
