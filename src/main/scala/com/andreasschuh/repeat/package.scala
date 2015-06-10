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

package com.andreasschuh

import scala.language.implicitConversions

import com.andreasschuh.repeat.core.Registration


/**
 * REPEAT workflow package
 */
package object repeat {

  /** Implicit conversion from registration ID/name to registration info object */
  implicit def stringToRegistration(name: String): Registration = Registration(name)

//  /** Start dataset pre-processing workflow */
//  def init(): Unit = {
//    val ex = Init().start
//    ex.waitUntilEnded
//  }
//
//  /** Start registration evaluation workflow */
//  def evaluate(reg: Registration): Unit = {
//    val ex = Evaluate(reg).start
//    ex.waitUntilEnded
//  }
}
