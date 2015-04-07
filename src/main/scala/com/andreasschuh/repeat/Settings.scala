package com.andreasschuh.repeat

import com.typesafe.config._
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
  protected def filePath(propName: String) = (new File(config.getString(propName))).getAbsoluteFile()

  /// Get string value
  protected def stringValue(propName: String) = config.getString(propName)

  /// Directory containing IRTK executables
  val irtkBinDir = filePath("irtk.bindir")

  /// Hostname of SLURM head node
  val slurmHost = stringValue("slurm.host")

  /// SLURM user name
  val slurmUser = stringValue("slurm.user")

  /// Authentication token for SLURM head node, e.g., name of SSH key file or password
  val slurmAuth = stringValue("slurm.auth")

  /// Subject ID of reference used for spatial normalization (e.g., MNI305)
  val refId = stringValue("repeat.reference.id")

  /// Template image used for spatial normalization
  val refIm = filePath("repeat.reference.image")

  /// CSV file listing subject IDs of images
  val imgCsv = filePath("repeat.image.csv")

  /// Image file name prefix (before subject ID)
  val imgPre = stringValue("repeat.image.prefix")

  /// Image file name suffix (after subject ID)
  val imgSuf = stringValue("repeat.image.suffix")

  /// Directory containing input images
  val imgIDir = filePath("repeat.image.idir")

  /// Output directory for transformed and resampled images
  val imgODir = filePath("repeat.image.odir")

  /// Segmentation image file name prefix (before subject ID)
  val segPre = stringValue("repeat.segmentation.prefix")

  /// Segmentation image file name suffix (after subject ID)
  val segSuf = stringValue("repeat.segmentation.suffix")

  /// Directory containing ground truth segmentation images
  val segIDir = filePath("repeat.segmentation.idir")

  /// Output directory for transformed and resampled segmentation images
  val segODir = filePath("repeat.segmentation.odir")

  /// Suffix/extension of output transformation files (e.g., ".dof" or ".dof.gz")
  val dofSuf = stringValue("repeat.dof.suffix")

  /// Output directory for transformation files
  val dofDir = filePath("repeat.dof.dir")
}
