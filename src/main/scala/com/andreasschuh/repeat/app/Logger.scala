package com.andreasschuh.repeat.app

import scala.sys.process.ProcessLogger


/**
 * OpenMOLE console output logger
 */
class Logger extends ProcessLogger {

  /// Ignore all output to STDOUT until OpenMOLE console is started up
  /// (in particular, don't print ASCII art OpenMOLE splash screen)
  protected var startedUp: Boolean = false

  def out(s: => String): Unit = {
    startedUp = startedUp || s.startsWith("OpenMOLE>")
    val ignore = !startedUp || s.contains("feature warning") ||
      "OpenMOLE>|import |[a-zA-Z_][a-zA-Z0-9_]*: ".r.findPrefixOf(s) != None
    if (!ignore) println(s)
  }

  def err(s: => String): Unit = {
    println(s)
  }

  def buffer[T](f: => T): T = f
}
