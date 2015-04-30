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

import com.typesafe.config.{ConfigFactory, Config => _Config, ConfigRenderOptions}
import java.io.File
import java.net.URL
import scala.collection.JavaConverters._
import FileUtil.normalize


/**
 * Global settings object
 */
object Config {
  private var _dir: File = new File(System.getProperty("user.dir"))
  private var _file: Option[File] = None
  private var _config: Option[Config] = None

  /** Change directory in which to look for default configuration file */
  def dir(dir: File): Unit = {
    _config = None
    _dir = dir
  }

  /** Local configuration file from which global configuration was loaded */
  def file = _file

  /** Get global configuration object */
  def apply(): Config = _config match {
    case None => load()
    case Some(s) => s
  }

  /**
   * Load global configuration, merging optional local HOCON file with reference
   *
   * This object reads the configuration from the following HOCON files:
   * 1. $PWD/@p name
   * 2. $HOME/.openmole/@p name
   * 3. $OPENMOLE/configuration/@p name
   * 4. $JAR/reference.conf
   * where:
   * - $PWD is the current working directory
   * - $HOME is the home directory of the user
   * - $OPENMOLE is the location of the OpenMOLE installation
   * - $JAR is the root of the OpenMOLE plugin .jar file
   *
   * @param name Name of local configuration file
   * @return Global configuration object
   */
  def load(name: String = "repeat.conf"): Config = {
    // User defined configuration file
    _file = {
      val localConfig1 = new File(_dir, name)
      val localConfig2 = new File(_dir, new File("Config", name).getPath)
      val localConfig3 = new File(_dir, new File("config", name).getPath)
      if      (localConfig1.exists()) Some(localConfig1.getAbsoluteFile)
      else if (localConfig2.exists()) Some(localConfig2.getAbsoluteFile)
      else if (localConfig3.exists()) Some(localConfig3.getAbsoluteFile)
      else {
        val homeConfig = new File(FileUtil.join(System.getProperty("user.home"), ".openmole", name))
        if (homeConfig.exists()) Some(homeConfig.getAbsoluteFile)
        else None
      }
    }
    // Parsed configuration object
    _config = Some(new Config(
      // Merge configuration with reference configuration
      ConfigFactory.defaultOverrides().withFallback(_file match {
        case Some(f) => ConfigFactory.parseFile(f)
        case None => ConfigFactory.empty()
      }).withFallback(ConfigFactory.defaultReference(getClass.getClassLoader)).resolve(),
      // Directory used to make relative paths in configuration absolute
      _dir.getAbsoluteFile
    ))
    _config.get
  }

  /**
   * Create global configuration from HOCON string
   * @param conf HOCON configuration string
   * @param base Path used to make relative paths in configuration absolute
   * @return Global configuration object
   */
  def parse(conf: String, base: String): Config = {
    _file = None
    _config = Some(new Config(ConfigFactory.parseString(conf).resolve(), new File(base).getAbsoluteFile))
    _config.get
  }
}

/**
 * Access values in configuration file
 */
class Config(val config: _Config, val base: File) {

  /** Get configuration as HOCON string */
  override def toString = config.root().render(ConfigRenderOptions.concise())

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