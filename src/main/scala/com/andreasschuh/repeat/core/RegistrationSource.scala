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

import org.openmole.core.workflow.mole._
import org.openmole.core.workflow.data._
import org.openmole.core.workflow.tools.ExpandedString


object RegistrationSource {

  def apply(regId: Prototype[String], reg: Prototype[Registration]) =
    new SourceBuilder {
      addInput(regId)
      addOutput(reg)
      def toSource = new RegistrationSource(regId, reg) with Built
    }

}

abstract class RegistrationSource(regId: Prototype[String], reg: Prototype[Registration]) extends Source {

  override def process(context: Context, executionContext: ExecutionContext)(implicit rng: RandomProvider) = {
    val name = context.option(regId).get
    Variable(reg, Registration(name))
  }
}