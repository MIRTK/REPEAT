package com.andreasschuh.repeat.puzzle

import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.sampling.Sampling
import org.openmole.core.workflow.data.Prototype
import org.openmole.core.workflow.data.Context
import org.openmole.plugin.task.scala._

import com.andreasschuh.repeat.core.Registration


/**
 * Task used to construct parId from parIdx (parSampling zipWithIndex parIdx)
 */
object SetParId {

  /**
   * @param reg    Registration info
   * @param parSmp Parameter sampling (rows of "params" CSV file)
   * @param parIdx Parameter set ID ("params" row index excl. header, zero-based)
   * @param parId  Parameter set ID as fixed width string with leading zeros (one-based)
   */
  def apply(reg: Registration, parSmp: Sampling, parIdx: Prototype[Int], parId: Prototype[String]) = {

    val parMaxId = parSmp.build(Context())(new util.Random()).size.toString
    val parWidth = math.max(parMaxId.length, 2)

    ScalaTask(s"""val ${parId.name} = f"$${${parIdx.name} + 1}%0${parWidth}d" """) set (
      name    := s"${reg.id}-SetParId",
      inputs  += parIdx,
      outputs += parId
    )
  }

}
