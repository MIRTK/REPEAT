/*
 * Registration Performance Assessment Tool (REPEAT)
 *
 * Copyright (C) 2015  Andreas Schuh
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
 */

package com.andreasschuh.repeat.core


/**
 * Registration object factory
 */
object Registration {
  def apply(name: String) = new Registration(name)

  /** Substitute <name> placeholder arguments by args("name") */
  def command(template: Cmd, args: Map[String, String]) = template.map(arg => arg match {
    case arg if arg.length > 2 && arg.head == '<' && arg.last == '>' => args(arg.tail.dropRight(1))
    case arg => arg
  })
}

/**
 * Configuration of registration command whose performance is to be assessed
 */
class Registration(val id: String) extends Configurable("registration." + id) {

  /** Split command string into list of arguments */
  protected def split(args: String): Cmd = """"(\\"|[^"])*?"|[^\s]+""".r.findAllIn(args).toIndexedSeq

  /** Get command string property value */
  protected def getCmdProperty(propName: String): Cmd = getStringProperty(propName) match {
    case cmd if cmd.length > 0 => split(cmd)
    case _ => throw new Exception(s"Property registration.$id.$propName cannot be empty")
  }

  /** Get optional command string property value */
  protected def getCmdOptionProperty(propName: String): Option[Cmd] = {
    getStringOptionProperty(propName) match {
      case Some(cmd) if cmd.length > 0 => Some(split(cmd))
      case _ => None
    }
  }

  /** Whether this registration is symmetric */
  val isSym = getBooleanOptionProperty("symmetric") getOrElse false

  /** CSV file with command parameters */
  val parCsv = getFileProperty("params")

  /** Output directory */
  val outDir = FileUtil.join(Workspace.dir, id)

  /** Directory of computed transformations */
  val dofDir = FileUtil.join(outDir, "dofs")

  /** Directory of deformed images */
  val imgDir = FileUtil.join(outDir, "images")

  /** Directory of propagated label images */
  val segDir = FileUtil.join(outDir, "labels")

  /** File name suffix for converted affine input transformation */
  val affSuf = getStringOptionProperty("aff-suffix") getOrElse Workspace.dofSuf

  /** File name suffix for output transformation */
  val phiSuf = getStringOptionProperty("phi-suffix") getOrElse Workspace.dofSuf

  /** Optional command used to convert affine transformation from IRTK format to required input format */
  val dof2aff = getCmdOptionProperty("dof2aff")

  /** Registration command */
  val command = getCmdProperty("command")

  /** Optional command which can be used to convert output transformation to IRTK format */
  val phi2dof = getCmdOptionProperty("phi2dof")
}
