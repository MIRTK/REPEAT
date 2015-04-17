package com.andreasschuh.repeat.core

import java.io.File

/**
 * Global settings object
 */
object GlobalSettings {
  private var defaultConfigDir: File = new File(System.getProperty("user.dir"))
  private var defaultConfigName: Option[String] = None
  private var defaultSettings: Option[Settings] = None

  /// Change directory in which to look for default configuration file
  def setConfigDir(dir: File): Unit = {
    defaultSettings = None
    defaultConfigDir = dir
  }

  /// Change configuration file from which global/default settings are read
  def setConfigName(name: String): Unit = {
    defaultSettings = None
    defaultConfigName = Some(name)
  }

  /// Get global/default settings instance
  def apply(): Settings = defaultSettings match {
    case None =>
      defaultSettings = Some(new Settings(defaultConfigName, defaultConfigDir))
      defaultSettings.get
    case Some(s) => s
  }
}