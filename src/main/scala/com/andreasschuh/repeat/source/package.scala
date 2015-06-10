package com.andreasschuh.repeat

import java.nio.file.Path
import scala.language.implicitConversions

import org.openmole.core.workflow.tools.ExpandedString


/**
 * Source helpers
 */
package object source {

  /** Implicit conversion of Path to ExpandedString */
  implicit def convertPathToExpandedString(p: Path): ExpandedString = p.toString

}
