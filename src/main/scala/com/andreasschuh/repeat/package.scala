package com.andreasschuh

import scala.language.implicitConversions

import com.andreasschuh.repeat.core.Registration
import com.andreasschuh.repeat.workflow.{Init, Evaluate}


/**
 * REPEAT workflow package
 */
package object repeat {

  /** Implicit conversion from registration ID/name to registration info object */
  implicit def stringToRegistration(name: String): Registration = Registration(name)

  /** Start dataset pre-processing workflow */
  def init() = Init().start

  /** Start registration evaluation workflow */
  def evaluate(reg: Registration) = Evaluate(reg).start
}
