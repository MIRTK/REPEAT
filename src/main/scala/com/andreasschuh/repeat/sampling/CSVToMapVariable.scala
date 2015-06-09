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

package com.andreasschuh.repeat.sampling

import java.io.{File, FileReader}

import au.com.bytecode.opencsv.CSVReader
import org.openmole.core.workflow.data.{Prototype, Variable, Context, RandomProvider}
import org.openmole.core.workflow.tools.ExpandedString


trait CSVToMapVariable {

  def separator: Char

  def toMapVariable(file: ExpandedString, p: Prototype[Map[String, String]], context: Context)(implicit rng: RandomProvider): Iterator[Iterable[Variable[_]]] = {
    val reader = new CSVReader(new FileReader(new File(file.from(context))), separator)
    val header = reader.readNext.toArray
    Iterator.continually(reader.readNext).takeWhile(_ != null).map {
      line ⇒ List(Variable(p, line.view.zipWithIndex.map {
        case (value, column) => header(column) -> value
      }.toMap))
    }
  }
}
