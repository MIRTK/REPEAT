package com.andreasschuh.repeat

import org.openmole.core.workspace.Workspace.authenticationProvider
import org.openmole.core.workflow.execution.local.LocalEnvironment
import org.openmole.plugin.environment.ssh.{ PrivateKey, SSHAuthentication }
import org.openmole.plugin.environment.slurm.SLURMEnvironment
import org.openmole.plugin.environment.condor.CondorEnvironment

/**
 * Settings common to all OpenMOLE workflows
 */
object Workflow extends Configurable("workflow") {

  /// Environment on which to execute parallel tasks
  val parEnv = {
    getStringProperty("environment").toLowerCase() match {
      case "slurm" => {
        SLURM.sshKey match {
          case Some(sshKey) => SSHAuthentication(0) = PrivateKey(sshKey, SLURM.user, "", SLURM.host)
          case None =>
        }
        SLURMEnvironment(SLURM.user, SLURM.host,
          queue = Some(SLURM.queueLong),
          threads = Some(1),
          memory = Some(4096),
          openMOLEMemory = Some(256)
        )
      }
      case "condor" => throw new Exception("Condor not yet supported by the REPEAT workflows")
      case "local" => LocalEnvironment(1)
    }
  }

  /// Whether to use symbolic links for input files instead of copying them to (remote) working directory
  val symLnk = getBooleanProperty("symlinks")

  /// Subject ID of reference used for spatial normalization (e.g., MNI305)
  val refId = getStringProperty("reference.id")

  /// Template image used for spatial normalization
  val refIm = getFileProperty("reference.image")

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
