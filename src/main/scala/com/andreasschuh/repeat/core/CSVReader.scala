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
import scala.io.Source


/**
 * CSV helpers
 */
object CSVReader {

  /**
   * Read CSV file header
   * @param file CSV file path.
   * @return List of header names.
   */
  def header(file: File) = {
    val src = Source.fromFile(file)
    try {
      src.getLines().take(1).toList.head.split(',')
    } finally {
      src.close()
    }
  }

  /**
   * Read CSV file
   * @param file CSV file path.
   */
  def fromFile(file: File) = {
    val src = Source.fromFile(file)
    try {
      src.getLines().map(_.split(",")).toArray.transpose.map(row => row.head -> row.tail).toMap
    } finally {
      src.close()
    }
  }
}
