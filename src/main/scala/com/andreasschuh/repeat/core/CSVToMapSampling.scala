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

import org.openmole.core.workflow.data.{Prototype, Variable, Context, RandomProvider}
import org.openmole.core.workflow.sampling.Sampling


object CSVToMapSampling {

  def apply(file: File, p: Prototype[Map[String, String]]) = new CSVToMapSamplingBuilder(file, p)
}

abstract class CSVToMapSampling(val file: File, p: Prototype[Map[String, String]]) extends Sampling with CSVToMapVariable {

  override def prototypes = List(p)

  override def build(context: ⇒ Context)(implicit rng: RandomProvider): Iterator[Iterable[Variable[_]]] = toMapVariable(file, p, context)

}
