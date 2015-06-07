package com.andreasschuh.repeat.puzzle

import org.openmole.core.workflow.mole._
import org.openmole.core.workflow.puzzle._
import org.openmole.core.workflow.task._
import org.openmole.core.workflow.transition._

object Strain {

  def apply(puzzle: Puzzle) = {
    val first = Capsule(EmptyTask(), strainer = true)
    val firstSlot = Slot(first)
    val last = Capsule(EmptyTask(), strainer = true)

    val action = firstSlot -- puzzle -- last
    val strain = firstSlot -- last

    Puzzle.merge(firstSlot, Seq(last), puzzles = Seq(action, strain))
  }

}
