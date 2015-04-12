package com.andreasschuh.repeat

import java.io.File

/**
 * A class or object whose fields and behavior are configured through the Settings
 */
abstract class Configurable(val propGroup: String = "") {

  protected val propPrefix = if (propGroup.isEmpty) "" else propGroup + "."

  /// Get absolute path of property in this group
  protected def getFileProperty(propName: String): File = GlobalSettings().getFile(propPrefix + propName)

  /// Get absolute path of property in this group
  protected def getPathProperty(propName: String): String = GlobalSettings().getPath(propPrefix + propName)

  /// Get string value of property in this group
  protected def getStringProperty(propName: String): String = GlobalSettings().getString(propPrefix + propName)

  /// Get boolean value of property in this group
  protected def getBooleanProperty(propName: String): Boolean = GlobalSettings().getBoolean(propPrefix + propName)

  /// Get integer value of property in this group
  protected def getIntProperty(propName: String): Int = GlobalSettings().getInt(propPrefix + propName)
}
