package com.andreasschuh.repeat

import java.io.File

/**
 * A class or object whose fields and behavior are configured through the Settings
 */
abstract class Configurable(val propGroup: String) {

  /// Get absolute path of property in this group
  protected def getFileProperty(propName: String): File = GlobalSettings().getFile(s"$propGroup.$propName")

  /// Get absolute path of property in this group
  protected def getPathProperty(propName: String): String = GlobalSettings().getPath(s"$propGroup.$propName")

  /// Get string value of property in this group
  protected def getStringProperty(propName: String): String = GlobalSettings().getString(s"$propGroup.$propName")

  /// Get boolean value of property in this group
  protected def getBooleanProperty(propName: String): Boolean = GlobalSettings().getBoolean(s"$propGroup.$propName")

  /// Get integer value of property in this group
  protected def getIntProperty(propName: String): Int = GlobalSettings().getInt(s"$propGroup.$propName")
}
