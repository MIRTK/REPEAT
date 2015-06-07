/*
 * Copyright (C) 2011 mathieu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andreasschuh.repeat.core

import org.openmole.core.workflow.data._
import org.openmole.core.workflow.sampling._
import org.openmole.core.workflow.tools.ExpandedString
import scala.util.Random


object CSVSampling {

  def apply(file: ExpandedString) = new CSVSamplingBuilder(file)
}


abstract class CSVSampling(val file: ExpandedString) extends Sampling with CSVToVariables {

  override def prototypes =
    columns.map { case (_, p) ⇒ p } :::
      fileColumns.map { case (_, _, p) ⇒ p } ::: Nil

  override def build(context: ⇒ Context)(implicit rng: RandomProvider): Iterator[Iterable[Variable[_]]] = toVariables(file, context)

}