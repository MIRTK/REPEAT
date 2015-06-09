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

import org.openmole.core.workflow.mole._
import org.openmole.core.workflow.data._


object ParamsCSVFileSource {

  def apply(regId: Prototype[String], dataSpace: Prototype[DataSpace], parCsv: Prototype[File]) =
    new SourceBuilder {
      addInput(regId)
      addInput(dataSpace)
      addOutput(parCsv)
      def toSource = new ParamsCSVFileSource(regId, dataSpace, parCsv) with Built
    }

}

abstract class ParamsCSVFileSource(regId: Prototype[String], dataSpace: Prototype[DataSpace], parCsv: Prototype[File]) extends Source {

  override def process(context: Context, executionContext: ExecutionContext)(implicit rng: RandomProvider) = {
    val regID = context.option(regId).get
    val space = context.option(dataSpace).get
    Variable(parCsv, space.parCsv(regID).toFile)
  }
}