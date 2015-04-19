package com.andreasschuh.repeat

/**
 * Created by as12312 on 18/04/15.
 */
package object core {

  /**
   * Type used for sequence of command name/path and arguments to be executed
   */
  type Cmd = Seq[String]

  object Cmd {
    def apply(name: String, args: String*): Cmd = Seq(name) ++ args.toSeq
  }

}
