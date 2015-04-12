package com.andreasschuh

/**
 * General workflow helpers
 */
package object repeat {

  /// Get list item or return default value if index is out-of-bounds
  def getOrElse(args: Seq[String], i: Int, default: String): String = if (0 <= i && i < args.length) args(i) else default
}
