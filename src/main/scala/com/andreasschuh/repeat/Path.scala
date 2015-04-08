package com.andreasschuh.repeat

import java.io.File

/**
 * File/directory path utilities
 */
object Path {

  /// Join two path strings
  def join(a: String, b: String) = new File(a, b).toString()

  /// Join file path and string
  def join(a: File, b: String) = new File(a, b).toString()

  /// Join multiple path strings
  def join(a: String, b: String, c: String*) = c.foldLeft(new File(a, b))((a, b) => new File(a, b)).toString()

  /// Join file path and multiple path strings
  def join(a: File, b: String, c: String*) = c.foldLeft(new File(a, b))((a, b) => new File(a, b)).toString()
}
