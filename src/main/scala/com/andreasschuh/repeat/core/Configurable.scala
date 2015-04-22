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

import java.io.File


/**
 * A class or object whose fields and behavior are configured through the Settings
 */
abstract class Configurable(val propGroup: String = "") {

  /** Common prefix of properties belonging to this Configurable object */
  protected val propPrefix = if (propGroup.isEmpty) "" else propGroup + "."

  /** Path of property in configuration */
  protected def getPropPath(propName: String) = if (propName(0) == '.') propName.drop(1) else propPrefix + propName

  /** Whether property value is set */
  protected def hasProperty(propName: String) = Config().hasPath(getPropPath(propName))

  /** Get set of keys of property in this group */
  protected def getPropertyKeySet(propName: String) = Config().getKeySet(getPropPath(propName))

  /** Get boolean value of property in this group */
  protected def getBooleanProperty(propName: String) = Config().getBoolean(getPropPath(propName))

  /** Get optional boolean value of property in this group */
  protected def getBooleanOptionProperty(propName: String) = {
    val propPath = getPropPath(propName)
    if (Config().hasPath(propPath)) Some(Config().getBoolean(propPath)) else None
  }

  /** Get integer value of property in this group */
  protected def getIntProperty(propName: String) = Config().getInt(getPropPath(propName))

  /** Get list of string values */
  protected def getIntListProperty(propName: String) = Config().getIntList(getPropPath(propName))

  /** Get string value of property in this group */
  protected def getStringProperty(propName: String) = Config().getString(getPropPath(propName))

  /** Get optional string value of property in this group */
  protected def getStringOptionProperty(propName: String) = {
    val propPath = getPropPath(propName)
    if (Config().hasPath(propPath)) Some(Config().getString(propPath)) else None
  }

  /** Get string value of property in this group */
  protected def getStringListProperty(propName: String) = Config().getStringList(getPropPath(propName))

  /** Get absolute path of property in this group */
  protected def getPathProperty(propName: String) = Config().getPath(getPropPath(propName))

  /** Get absolute path of property in this group */
  protected def getFileProperty(propName: String) = Config().getFile(getPropPath(propName))

  /** Split command string into list of arguments */
  protected def split(args: String): Cmd = """"(\\"|[^"])*?"|[^\s]+""".r.findAllIn(args).toIndexedSeq

  /** Get command string property value */
  protected def getCmdProperty(propName: String): Cmd = getStringProperty(propName) match {
    case cmd if cmd.length > 0 => split(cmd)
    case _ => throw new Exception(s"Property $propName cannot be an empty string")
  }

  /** Get optional command string property value */
  protected def getCmdOptionProperty(propName: String): Option[Cmd] = {
    getStringOptionProperty(propName) match {
      case Some(cmd) if cmd.length > 0 => Some(split(cmd))
      case _ => None
    }
  }
}
