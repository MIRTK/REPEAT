package com.andreasschuh.repeat

import com.typesafe.config.ConfigFactory
import java.io.File
import java.net.URL

/**
 * Access values in configuration file
 *
 * This object reads the configuration from the following HOCON files:
 * 1. /repeat.conf
 * 2. $HOME/.openmole/repeat.conf
 * 3. $OPENMOLE/configuration/repeat.conf
 * 4. $JAR/reference.conf
 * where:
 * - $PWD is the current working directory
 * - $HOME is the home directory of the user
 * - $OPENMOLE is the location of the OpenMOLE installation
 * - $JAR is the root of the OpenMOLE plugin .jar file
 */
object Settings {

  /// Parsed configuration object
  private val config = {
    val home = System.getProperty("user.home")
    val openmole = Option(System.getProperty("openmole.location"))
    val reference = openmole match {
      case Some(_) => ConfigFactory.parseURL(new URL("platform:/plugin/com.andreasschuh.repeat/reference.conf"))
      case None => ConfigFactory.load()
    }
    val shared = ConfigFactory.parseFile(new File(s"$openmole/configuration/repeat.conf"))
    val user = ConfigFactory.parseFile(new File(s"$home/.openmole/repeat.conf"))
    val local = ConfigFactory.parseFile(new File("repeat.conf"))
    local.withFallback(user).withFallback(shared).withFallback(reference).resolve()
  }

  /// Get absolute path
  def getFile(propName: String): File = new File(config.getString(propName)).getAbsoluteFile()

  /// Get absolute path string
  def getPath(propName: String): String = new File(config.getString(propName)).getAbsolutePath()

  /// Get string value
  def getString(propName: String): String = config.getString(propName)

  /// Get boolean value
  def getBoolean(propName: String): Boolean = config.getBoolean(propName)
}
