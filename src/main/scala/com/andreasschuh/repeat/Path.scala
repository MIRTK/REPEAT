package com.andreasschuh.repeat

import java.io.File

/**
 * File/directory path utilities
 */
object Path {

  /// Delete file at specified path if it exists
  def delete(a: String): Unit = {
    val f = new File(a)
    if (f.exists) f.delete()
  }

  /// Join two path strings
  def join(a: String, b: String): String = {
    val file = new File(b)
    if (file.isAbsolute) file.getPath()
    else new File(a, b).toString
  }

  /// Join multiple path strings
  def join(a: String, b: String, c: String*): String = c.foldLeft(join(a, b))((a, b) => join(a, b))

  /// Join file path and string
  def join(a: File, b: String): File = {
    val file = new File(b)
    if (file.isAbsolute) file
    else new File(a, b)
  }

  /// Join file path and multiple path strings
  def join(a: File, b: String, c: String*): File = c.foldLeft(join(a, b))((a, b) => join(a, b))
}
