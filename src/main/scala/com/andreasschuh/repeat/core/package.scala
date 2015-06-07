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

import java.nio.file.{Path, Paths}


/**
 * The core of the REPEAT package
 */
package object core {

  /** Type used for sequence of command name/path and arguments to be executed */
  type Cmd = Seq[String]

  /** Construct command name and arguments sequence */
  object Cmd {

    /** Construct command arguments from strings */
    def apply(argv: String*): Cmd = argv.toSeq

    /** Substitute placeholder arguments by args("name") */
    def apply(template: Cmd, args: Map[String, String]): Cmd = expand(template, args)

    /** Make properly quoted string from command arguments */
    def toString(cmd: Cmd) = cmd.mkString("\"", "\" \"", "\"")
  }

  /** Replace occurrences of ${name} in s by v("name") */
  def expand(s: String, v: Map[String, _]): String = {
    val getGroup = (_: scala.util.matching.Regex.Match) group 1
    "\\$\\{([^}]*)\\}".r.replaceSomeIn(s, getGroup andThen v.lift andThen (_ map (_.toString)))
  }

  /** Replace occurrences of ${name} in s by v("name") */
  def expand(l: Seq[String], v: Map[String, _]): Seq[String] = l.map(s => expand(s, v))

  /** Replace occurrences of ${name} in s by v("name") */
  def expand(p: Path, v: Map[String, _]): Path = Paths.get(expand(p.toString, v))

  /** For use of regular expressions in match cases */
  implicit class Regex(sc: StringContext) {
    def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }
}
