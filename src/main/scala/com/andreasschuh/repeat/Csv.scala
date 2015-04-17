package com.andreasschuh.repeat

import java.io.File
import scala.io.Source

/**
 * CSV helpers
 */
object Csv {

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
