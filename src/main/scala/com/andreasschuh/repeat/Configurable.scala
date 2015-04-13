package com.andreasschuh.repeat

import java.io.File

/**
 * A class or object whose fields and behavior are configured through the Settings
 */
abstract class Configurable(val propGroup: String = "") {

  /// Common prefix of properties belonging to this Configurable object
  protected val propPrefix = if (propGroup.isEmpty) "" else propGroup + "."

  /// Path of property in configuration
  protected def getPropPath(propName: String) = if (propName(0) == '.') propName.drop(1) else propPrefix + propName

  /// Get set of keys of property in this group
  protected def getPropertyKeySet(propName: String) = GlobalSettings().getKeySet(getPropPath(propName))

  /// Get boolean value of property in this group
  protected def getBooleanProperty(propName: String) = GlobalSettings().getBoolean(getPropPath(propName))

  /// Get integer value of property in this group
  protected def getIntProperty(propName: String) = GlobalSettings().getInt(getPropPath(propName))

  /// Get list of string values
  protected def getIntListProperty(propName: String) = GlobalSettings().getIntList(getPropPath(propName))

  /// Get string value of property in this group
  protected def getStringProperty(propName: String) = GlobalSettings().getString(getPropPath(propName))

  /// Get string value of property in this group
  protected def getStringListProperty(propName: String) = GlobalSettings().getStringList(getPropPath(propName))

  /// Get absolute path of property in this group
  protected def getPathProperty(propName: String) = GlobalSettings().getPath(getPropPath(propName))

  /// Get absolute path of property in this group
  protected def getFileProperty(propName: String) = GlobalSettings().getFile(getPropPath(propName))
}
