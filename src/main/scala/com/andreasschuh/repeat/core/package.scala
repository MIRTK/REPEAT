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

/**
 * The core of the REPEAT package
 */
package object core {

  /** Type used for sequence of command name/path and arguments to be executed */
  type Cmd = Seq[String]

  /** Construct command name and arguments sequence */
  object Cmd {
    def apply(argv: String*): Cmd = argv.toSeq
  }

  /** Replace occurrences of ${name} in s by v("name") */
  def interpolate(s: String, v: Map[String, _]): String = {
    val getGroup = (_: scala.util.matching.Regex.Match) group 1
    "\\$\\{([^}]*)\\}".r.replaceSomeIn(s, getGroup andThen v.lift andThen (_ map (_.toString)))
  }

  /** Replace occurrences of ${name} in s by v("name") */
  def interpolate(l: Seq[String], v: Map[String, _]): Seq[String] = l.map(s => interpolate(s, v))
}
