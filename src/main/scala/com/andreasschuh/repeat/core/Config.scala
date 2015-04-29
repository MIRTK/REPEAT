/*
 * Registration Performance Assessment Tool (REPEAT)
 *
 * Copyright (C) 2015  Andreas Schuh
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
 */

package com.andreasschuh.repeat.core

import com.typesafe.config.ConfigFactory
import java.io.File
import java.net.URL
import scala.collection.JavaConverters._
import FileUtil.normalize


/**
 * Global settings object
 */
object Config {
  private var _dir: File = new File(System.getProperty("user.dir"))
  private var _base: Option[File] = None
  private var _name: Option[String] = None
  private var _config: Option[Config] = None

  /** Change directory in which to look for default configuration file */
  def dir(dir: File, base: String = ""): Unit = {
    _config = None
    _dir = dir
    _base = if (base.isEmpty) None else Some(new File(base))
  }

  /** Change configuration file from which global/default settings are read */
  def name(name: String): Unit = {
    _config = None
    _name = Some(name)
  }

  /** Get global/default settings instance */
  def apply(): Config = _config match {
    case None =>
      _config = Some(new Config(_name, _dir, _base))
      _config.get
    case Some(s) => s
  }
}

/**
 * Access values in configuration file
 *
 * This object reads the configuration from the following HOCON files:
 * 1. $PWD/repeat.conf
 * 2. $HOME/.openmole/repeat.conf
 * 3. $OPENMOLE/configuration/repeat.conf
 * 4. $JAR/reference.conf
 * where:
 * - $PWD is the current working directory
 * - $HOME is the home directory of the user
 * - $OPENMOLE is the location of the OpenMOLE installation
 * - $JAR is the root of the OpenMOLE plugin .jar file
 */
class Config(configName: Option[String] = None,
             configDir: File = new File(System.getProperty("user.dir")),
             baseDir: Option[File] = None) {

  /** Found (main) configuration file */
  val file: Option[File] = configName match {
    case Some(name) =>
      val file = new File(configDir, name)
      if (!file.exists()) throw new Exception("Configuration file does not exist: " + file.getAbsolutePath)
      Some(file)
    case None =>
      val localConfig1 = new File(configDir, "repeat.conf")
      val localConfig2 = new File(configDir, "Config/repeat.conf")
      val localConfig3 = new File(configDir, "config/repeat.conf")
      if      (localConfig1.exists()) Some(localConfig1.getAbsoluteFile)
      else if (localConfig2.exists()) Some(localConfig2.getAbsoluteFile)
      else if (localConfig3.exists()) Some(localConfig3.getAbsoluteFile)
      else {
        val homeConfig = new File(FileUtil.join(System.getProperty("user.home"), ".openmole", "repeat.conf"))
        if (homeConfig.exists()) Some(homeConfig.getAbsoluteFile)
        else None
      }
  }

  /** Directory used to make relative file paths absolute */
  val base: File = baseDir.getOrElse(configDir).getAbsoluteFile

  /** Parsed configuration object */
  private val config = {
    ConfigFactory.defaultOverrides().withFallback(file match {
      case Some(f) => ConfigFactory.parseFile(f)
      case None => ConfigFactory.empty()
    }).withFallback(System.getProperty("eclipse.application", "NotOpenMOLE") match {
      case "org.openmole.ui" => ConfigFactory.parseURL(new URL("platform:/plugin/com.andreasschuh.repeat/reference.conf"))
      case _ => ConfigFactory.defaultReference()
    }).resolve()
  }

  /** Whether value is set */
  def hasPath(propName: String): Boolean = config.hasPath(propName)

  /** Get set of keys */
  def getKeySet(propName: String): Set[String] = config.getObject(propName).keySet.asScala.toSet

  /** Get boolean value */
  def getBoolean(propName: String): Boolean = config.getBoolean(propName)

  /** Get integer value */
  def getInt(propName: String): Int = config.getInt(propName)

  /** Get list of string values */
  def getIntList(propName: String): Seq[Int] = config.getIntList(propName).asScala.map(_.intValue)

  /** Get string value */
  def getString(propName: String): String = config.getString(propName)

  /** Get list of string values */
  def getStringList(propName: String): Seq[String] = config.getStringList(propName).asScala

  /** Get file path as java.io.File */
  def getFile(propName: String): File = {
    val f = new File(config.getString(propName))
    normalize(if (f.isAbsolute) f else new File(base, f.getPath))
  }

  /** Get file path as java.nio.file.Path */
  def getPath(propName: String) = getFile(propName).toPath

}