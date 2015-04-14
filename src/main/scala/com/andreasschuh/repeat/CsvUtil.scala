package com.andreasschuh.repeat

import java.io.File

/**
 * CSV output helpers
 */
object CsvUtil {

  def writeHeaderIfFileNotExists(f: File, header: Seq[String]): Unit = if (!f.exists) {
    f.getParentFile.mkdirs()
    val writer = new java.io.FileWriter(f, false)
    try {
      writer.write(header.mkString(","))
      writer.write("\n")
    } finally {
      writer.close()
    }
  }

}
