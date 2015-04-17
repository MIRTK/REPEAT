package com.andreasschuh.repeat

/**
 * General workflow helpers
 */
package object workflow {

  /// Get list item or return default value if index is out-of-bounds
  def getOrElse(args: Seq[String], i: Int, default: String) = if (args.isDefinedAt(i)) args(i) else default
}
