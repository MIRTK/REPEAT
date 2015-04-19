package com.andreasschuh.repeat.core

import java.io.File
import java.io.FileWriter
import scala.sys.process.ProcessLogger

/**
 * File process logger
 */
class TaskLogger(log: File) extends Configurable("workspace.logs") with ProcessLogger {

  /// File writer object
  protected val writer = {
    val logFile = log.getAbsoluteFile
    logFile.getParentFile.mkdirs()
    new FileWriter(logFile, getBooleanProperty("append"))
  }

  /// Whether to flush buffers after each line read from STDOUT (STDERR is always written immediately)
  val flush = getBooleanProperty("flush")

  /// Whether to
  val tee = getBooleanProperty("tee")

  /// Write line of process' STDOUT
  def out(s: => String): Unit = {
    writer.write(s)
    writer.write('\n')
    if (flush) writer.flush()
    if (tee) println(s)
  }

  /// Write line of process' STDERR
  def err(s: => String): Unit = {
    writer.write(s)
    writer.write('\n')
    writer.flush()
    if (tee) println(s)
  }

  /// Wrap process execution and close file when finished
  def buffer[T](f: => T): T = {
    val returnValue: T = f
    writer.close()
    returnValue
  }
}
