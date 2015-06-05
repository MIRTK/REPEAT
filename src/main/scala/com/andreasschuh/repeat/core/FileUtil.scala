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

  /** Prepend dot on Linux and "_" on Windows to make file/directory "hidden" */
  def hidden(a: String) = (if ("(Linux|Mac OS X)".r.findPrefixOf(System.getProperty("os.name")) != None) "." else "_") + a

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

  /** Get file name */
  def getName(path: String) = new File(path).getName

  /** Get file name extension */
  def getExtension(name: String): String = name.indexOf('.', 1) match {
    case i if i > 0 => name.substring(i)
    case _ => ""
  }

  /** Get file name extension */
  def getExtension(f: File): String = getExtension(f.getName)

  /** Backup file if it exists */
  def backup(f: File, rm: Boolean = false): Option[File] = {
    if (f.exists) {
      val ext  = getExtension(f)
      val base = f.getPath.dropRight(ext.length)
      var idx  = 1
      val max  = 100
      var bak  = new File(s"$base-$idx$ext")
      while (bak.exists && idx < max) {
        idx = idx + 1
        bak = new File(s"$base-$idx$ext")
      }
      if (idx == max) {
        idx = 1
        bak = new File(s"$base-$idx$ext")
      }
      copy(f, bak)
      if (rm) f.delete()
      Some(bak)
    }
    else None
  }

  /** Backup file if it exists */
  def backup(f: String, rm: Boolean): Unit = backup(new File(f), rm)

  /** Make parent directories (of file) */
  def mkdirs(file: File) = {
    val parent = file.getParentFile
    if (parent != null) parent.mkdirs()
  }

  /** Get relative file path */
  def relativize(base: File, path: File): File = base.toPath.relativize(path.toPath).toFile

  /** Get relative file path */
  def relativize(base: File, path: String): String = relativize(base, new File(path)).getPath

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
