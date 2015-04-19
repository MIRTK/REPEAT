package com.andreasschuh

/**
 * OpenMOLE REPEAT plugin
 */
package object repeat {

  /**
   * Get list item or return default value if index is out-of-bounds
   * @param seq Sequence of items
   * @param i List index
   * @param default Default value
   * @return List item if index is within bounds or default value otherwise
   */
  def getOrElse(seq: Seq[String], i: Int, default: String) = if (seq.isDefinedAt(i)) seq(i) else default
}
