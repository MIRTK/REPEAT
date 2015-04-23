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
import java.nio.file.Files


/**
 * File/directory path utilities
 */
object FileUtil {

  /** Delete file at specified path if it exists */
  def delete(a: String): Unit = {
    val f = new File(a)
    if (f.exists) f.delete()
  }

  /**
   * Copy file if it was modified
   *
   * @param src File to copy
   * @param dst Copy of file
   */
  def copy(src: File, dst: File): Unit = {
    if (dst != src && dst.lastModified() < src.lastModified()) {
      val parent = dst.getParentFile
      if (parent != null) parent.mkdirs()
      Files.copy(src.toPath, dst.toPath)
    }
  }

  /** Get file name extension */
  def getExtension(f: File) = f.getName.indexOf('.', 1) match {
    case i if i > 0 => f.getName.substring(i)
    case _ => ""
  }

  /** Make parent directories (of file) */
  def mkdirs(file: File) = {
    val parent = file.getParentFile
    if (parent != null) parent.mkdirs()
  }

  /** Get relative file path */
  def relativize(base: File, path: File) = base.toPath.relativize(path.toPath).toFile

  /** Normalize/clean up path */
  def normalize(path: File) = path.toPath.normalize.toFile

  /** Join two path strings */
  def join(a: String, b: String): String = {
    val file = new File(b)
    if (file.isAbsolute) file.getPath
    else new File(a, b).toString
  }

  /** Join multiple path strings */
  def join(a: String, b: String, c: String*): String = c.foldLeft(join(a, b))((a, b) => join(a, b))

  /** Join file path and string */
  def join(a: File, b: String): File = {
    val file = new File(b)
    if (file.isAbsolute) file
    else new File(a, b)
  }

  /** Join file path and multiple path strings */
  def join(a: File, b: String, c: String*): File = c.foldLeft(join(a, b))((a, b) => join(a, b))
}
