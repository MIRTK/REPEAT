package com.andreasschuh.repeat

import com.github.kxbmap.configs._
import com.typesafe.config.ConfigFactory
import java.io.File
import java.net.URL

/**
 * Global settings such as file paths and environment
 *
 * This object reads the configuration from the reference.conf file
 * found in the jar archive of the OpenMOLE plugin (OSGi bundle).
 */
object Settings {

  /// Parsed configuration object
  protected val config = {
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
  protected def filePath(propName: String) = (new File(config.get[String](propName))).getAbsoluteFile()

  /// Directory containing IRTK executables
  val irtkBinDir = filePath("irtk.bindir")

  /// Subject ID of reference used for spatial normalization (e.g., MNI305)
  val refId = config.get[String]("repeat.reference.id")

  /// Template image used for spatial normalization
  val refIm = filePath("repeat.reference.image")

  /// CSV file listing subject IDs of images
  val imgCsv = filePath("repeat.image.csv")

  /// Image file name prefix (before subject ID)
  val imgPre = config.get[String]("repeat.image.prefix")

  /// Image file name suffix (after subject ID)
  val imgSuf = config.get[String]("repeat.image.suffix")

  /// Directory containing input images
  val imgIDir = filePath("repeat.image.idir")

  /// Output directory for transformed and resampled images
  val imgODir = filePath("repeat.image.odir")

  /// Segmentation image file name prefix (before subject ID)
  val segPre = config.get[String]("repeat.segmentation.prefix")

  /// Segmentation image file name suffix (after subject ID)
  val segSuf = config.get[String]("repeat.segmentation.suffix")

  /// Directory containing ground truth segmentation images
  val segIDir = filePath("repeat.segmentation.idir")

  /// Output directory for transformed and resampled segmentation images
  val segODir = filePath("repeat.segmentation.odir")

  /// Suffix/extension of output transformation files (e.g., ".dof" or ".dof.gz")
  val dofSuf = config.get[String]("repeat.dof.suffix")

  /// Output directory for transformation files
  val dofDir = filePath("repeat.dof.dir")
}
