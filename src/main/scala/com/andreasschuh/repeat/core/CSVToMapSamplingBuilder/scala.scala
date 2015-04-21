/*
 * Copyright (C) 2015 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
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

import java.io.File

import org.openmole.core.workflow.builder.SamplingBuilder
import org.openmole.core.workflow.data.Prototype

import scala.collection.mutable.ListBuffer

class CSVToMapSamplingBuilder(file: File, p: Prototype[Map[String, String]]) extends SamplingBuilder { builder ⇒
  private var separator: Option[Char] = None

  def setSeparator(s: Option[Char]) = {
    separator = s
    this
  }

  def toSampling = new CSVToMapSampling(file, p) {
    val separator = builder.separator.getOrElse(',')
  }
}
