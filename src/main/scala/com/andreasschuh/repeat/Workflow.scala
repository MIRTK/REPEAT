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

}
