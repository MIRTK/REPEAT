package com.andreasschuh.repeat

/**
 * Settings common to all OpenMOLE workflows and tasks
 */
object Constants extends Configurable {

  /// Subject ID of reference used for spatial normalization (e.g., MNI305)
  val refId = getStringProperty("template.id")

  /// Template image used for spatial normalization
  val refIm = getFileProperty("template.image")

  /// CSV file listing subject IDs of images
  val imgCsv = getFileProperty("image.csv")

  /// Image file name prefix (before subject ID)
  val imgPre = getStringProperty("image.prefix")

  /// Image file name suffix (after subject ID)
  val imgSuf = getStringProperty("image.suffix")

  /// Directory containing input images
  val imgIDir = getFileProperty("image.idir")

  /// Output directory for transformed and resampled images
  val imgODir = getFileProperty("image.odir")

  /// Segmentation image file name prefix (before subject ID)
  val segPre = getStringProperty("segmentation.prefix")

  /// Segmentation image file name suffix (after subject ID)
  val segSuf = getStringProperty("segmentation.suffix")

  /// Directory containing ground truth segmentation images
  val segIDir = getFileProperty("segmentation.idir")

  /// Output directory for transformed and resampled segmentation images
  val segODir = getFileProperty("segmentation.odir")

  /// Output directory for transformation files
  val dofDir = getFileProperty("dof.dir")

  /// Suffix/extension of output transformation files (e.g., ".dof" or ".dof.gz")
  val dofSuf = getStringProperty("dof.suffix")

  /// Output directory for process log files
  val logDir = getFileProperty("log.dir")

  /// Suffix/extension of output log files (e.g., ".log" or ".txt")
  val logSuf = getStringProperty("log.suffix")
}
