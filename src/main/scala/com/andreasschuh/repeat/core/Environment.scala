package com.andreasschuh.repeat.core

import org.openmole.core.workspace.{ Workspace => OpenMOLEWorkspace }
import org.openmole.core.workflow.execution.local.LocalEnvironment
import org.openmole.plugin.environment.ssh.{ PrivateKey, SSHAuthentication }
import org.openmole.plugin.environment.slurm.SLURMEnvironment
import org.openmole.plugin.environment.condor.CondorEnvironment

/**
 * Settings common to all OpenMOLE workflows
 */
object Environment extends Configurable("environment") {

  /// Whether to use symbolic links for input files instead of copying them to (remote) working directory
  val symLnk = getBooleanProperty("links")

  /// Get environment for the named task category
  protected def getEnvironmentProperty(propName: String) = {
    getStringProperty(propName).toLowerCase match {
      case "slurm" =>
        SLURM.sshKey match {
          case Some(sshKey) => SSHAuthentication(0) = PrivateKey(sshKey, SLURM.user, "", SLURM.host)
          case None =>
        }
        SLURMEnvironment(SLURM.user, SLURM.host,
          queue = Some(SLURM.queue(propName)),
          threads = Some(1),
          memory = Some(4096),
          openMOLEMemory = Some(256)
        )(OpenMOLEWorkspace.instance.authenticationProvider)
      case "condor" => throw new Exception("Condor not yet supported by REPEAT")
      case "local" => local
    }
  }

  /** Environment for parallel tasks executed on the local machine */
  val local = LocalEnvironment(getIntProperty("nodes") match {
    case n if n <= 0 => Runtime.getRuntime.availableProcessors()
    case n if n >  0 => n
  })

  /** Environment on which to execute short running tasks */
  val short = getEnvironmentProperty("short")

  /** Environment on which to execute long running tasks */
  val long = getEnvironmentProperty("long")

  /** Whether all tasks are executed on local machine */
  val localOnly = (getStringProperty("short").toLowerCase == "local") && (getStringProperty("long").toLowerCase == "local")
}
