package com.andreasschuh.repeat

import scala.language.implicitConversions
import org.openmole.core.workflow.puzzle.Puzzle


/**
 * Workflow puzzles package object
 */
package object puzzle {

  /** Implicit conversion of workflow puzzle to OpenMOLE puzzle */
  implicit def workflowToPuzzle(workflow: Workflow): Puzzle = workflow.puzzle

}
