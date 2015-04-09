package com.andreasschuh.repeat

import java.io.File

/**
 * A class or object whose fields and behavior are configured through the Settings
 */
abstract class Configurable(val propGroup: String) {

  /// Get absolute path of property in this group
  protected def getFileProperty(propName: String): File = Settings.getFile(s"$propGroup.$propName")

  /// Get absolute path of property in this group
  protected def getPathProperty(propName: String): String = Settings.getPath(s"$propGroup.$propName")

  /// Get string value of property in this group
  protected def getStringProperty(propName: String): String = Settings.getString(s"$propGroup.$propName")

  /// Get boolean value of property in this group
  protected def getBooleanProperty(propName: String): Boolean = Settings.getBoolean(s"$propGroup.$propName")
}
