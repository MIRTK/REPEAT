// =============================================================================
// Project: Registration Performance Assessment Tool (REPEAT)
// Module:  OpenMOLE setup script
//
// Copyright (c) 2015, Andreas Schuh.
// See LICENSE file for license information.
// =============================================================================

import com.andreasschuh.repeat._

// Environment on which to execute registration tasks
val env = {
  Workflow.useEnv match {
    case "slurm" => {
      SLURM.sshKey match {
        case Some(sshKey) => {
          println("Enter password of private SSH key (for SLURM authentication):")
          SSHAuthentication(0) = PrivateKey(sshKey, SLURM.user, encrypted, SLURM.host)
        }
        case None => {}
      }
      SLURMEnvironment(SLURM.user, SLURM.host, queue=SLURM.queueLong, threads=1, memory=4096, openMOLEMemory=256)
    }
    case "local" => LocalEnvironment(1)
    case v => LocalEnvironment(v.toInt)
  }
}
