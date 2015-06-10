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

import java.nio.file.{Path, Paths}


/**
 * Registration runtime measurements
 */
object Time extends Configurable("evaluation.runtime") {

  /** Process mode whose runtime is measured */
  sealed abstract class Mode(i: Int, val label: String) { def toInt: Int = i }

  case object User   extends Mode(0, "user")
  case object System extends Mode(1, "system")
  case object Total  extends Mode(2, "total")
  case object Real   extends Mode(3, "real")

  /** Convert string to runtime measurement process mode */
  object Mode {
    def apply(s: String) = s.toLowerCase match {
      case "user" | "usr" => User
      case "system" | "sys" => System
      case "total" | "user+system" | "usr+sys" => Total
      case "real" => Real
      case _ => throw new Exception("Invalid runtime measurement process mode: " + s)
    }
  }

  /** Time unit enumeration value */
  sealed abstract class Unit(i: Int) { def toInt: Int = i }

  case object Seconds extends Unit(0)
  case object Minutes extends Unit(1)
  case object Hours   extends Unit(2)

  /** Convert string to time unit */
  object Unit {
    def apply(s: String) = s.toLowerCase match {
      case "s" | "sec" | "secs" | "second" | "seconds" => Seconds
      case "m" | "min" | "mins" | "minute" | "minutes" => Minutes
      case "h" | "hr" | "hrs" | "hour" | "hours" => Hours
      case _ => throw new Exception("Invalid time unit: " + s)
    }
  }

  /** List of process modes for which to report runtime measurements */
  val modes = getStringListProperty("measure").map(Mode(_))

  /** Unit of runtime measurements */
  val units = Unit(getStringProperty("units"))

  /** Template path of CSV file for individual registration runtime measurements */
  val resCsv = WorkSpace.csvDir.resolve(getStringProperty("results")).normalize.toString

  /** Get path of CSV file for individual registration runtime measurements */
  def resCsv(setId: String, regId: String, parId: String): Path =
    Paths.get(expand(resCsv, Map("setId" -> setId, "regId" -> regId, "parId" -> parId)))

  /** Template path of CSV file for average registration runtime measurements */
  val avgCsv = WorkSpace.csvDir.resolve(getStringProperty("summary")).normalize.toString

  /** Get path of CSV file for average registration runtime measurements */
  def avgCsv(setId: String): Path = Paths.get(expand(avgCsv, Map("setId" -> setId)))

}
