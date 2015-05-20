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

import java.util.Calendar
import java.text.SimpleDateFormat


/**
 * Constants used as prefix in output messages
 */
object Prefix {
  private lazy val dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm.ss")
  def TIME = dateFormat.format(Calendar.getInstance.getTime)
  def NAME = "[REPEAT " + TIME + "] "
  def INFO = NAME + "Info: "
  def SKIP = NAME + "Skip: "
  def QSUB = NAME + "QSub: "
  def DONE = NAME + "Done: "
  def WARN = NAME + "Warn: "
}
