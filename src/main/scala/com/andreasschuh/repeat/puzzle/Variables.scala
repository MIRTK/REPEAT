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

import java.io.File
import java.nio.file.Paths
import scala.language.reflectiveCalls

import org.openmole.core.dsl._


/**
 * Declaration of common workflow puzzle variable prototypes
 */
object Variables {
  val go     = Val[Boolean]
  val regId  = Val[String]
  val parVal = Val[Map[String, String]]
  val parIdx = Val[Int]
  val parId  = Val[String]
  val refId  = Val[String]
  val tgtId  = Val[String]
  val srcId  = Val[String]
  val imgId  = Val[String]
}
