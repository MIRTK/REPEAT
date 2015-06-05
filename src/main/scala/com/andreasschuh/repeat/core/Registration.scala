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

import FileUtil.{join, normalize}


/**
 * Registration object factory
 */
object Registration {

  /** Runtime measurements */
  lazy val times = List("User", "System", "Total", "Real")

  /** Create info object for named registration */
  def apply(name: String) = new Registration(name)

  /** Substitute placeholder arguments by args("name") */
  def command(template: Cmd, args: Map[String, String]) = interpolate(template, args)
}


/**
 * Configuration of registration command whose performance is to be assessed
 */
class Registration(val id: String) extends Configurable("registration." + id) {

  /** Whether this registration is symmetric */
  val isSym = getBooleanOptionProperty("symmetric") getOrElse false

  /** CSV file with command parameters */
  val parCsv = getFileProperty("params")

  /** Template configuration file content for registration command */
  val config = getStringOptionProperty("config")

  /** Optional command used to convert affine transformation from IRTK format to required input format */
  val dof2affCmd = getCmdOptionProperty("dof2aff")

  /** Directory of converted affine input transformations */
  val affDir = dof2affCmd match {
    case Some(_) => normalize(join(Workspace.dir, getStringProperty(".workspace.output.affs")))
    case None => Workspace.dofAff
  }

  /** Directory of registration log files */
  val logDir = normalize(join(Workspace.logDir, "${regId}-${parId}"))

  /** Directory of computed transformations */
  val dofDir = normalize(join(Workspace.dir, getStringProperty(".workspace.output.dofs")))

  /** Directory of deformed images */
  val imgDir = normalize(join(Workspace.dir, getStringProperty(".workspace.output.images")))

  /** Directory of propagated label images */
  val segDir = normalize(join(Workspace.dir, getStringProperty(".workspace.output.labels")))

  /** Directory of Jacobian determinant maps */
  val jacDir = normalize(join(Workspace.dir, getStringProperty(".workspace.output.jacobians")))

  /** Directory of evaluation results */
  val resDir = normalize(join(Workspace.dir, getStringProperty(".workspace.output.results")))

  /** Directory of summary evaluation results */
  val sumDir = normalize(join(Workspace.dir, getStringProperty(".workspace.output.summary")))

  /** File name suffix for Jacobian determinant map */
  val jacSuf = getStringOptionProperty("jac-suffix") getOrElse IRTK.jacSuf

  /** File name suffix for converted affine input transformation */
  val affSuf = getStringOptionProperty("aff-suffix") getOrElse Workspace.dofSuf

  /** File name suffix for output transformation */
  val phiSuf = getStringOptionProperty("phi-suffix") getOrElse Workspace.dofSuf

  /** Command to run image registration */
  val runCmd = getCmdProperty("command")

  /** Execution environment for registration command */
  val runEnv = Environment(name    = getStringOptionProperty("environment"),
                           memory  = getIntOptionProperty("memory"),
                           nodes   = getIntOptionProperty("nodes"),
                           threads = getIntOptionProperty("threads"))

  /** Optional command which can be used to convert output transformation to IRTK format */
  val phi2dofCmd = getCmdOptionProperty("phi2dof")

  /** File name suffix of (converted) output transformation */
  val dofSuf = phi2dofCmd match {
    case Some(_) => Workspace.dofSuf
    case None => phiSuf
  }

  /** Command used to deform an image */
  val deformImageCmd = getCmdOptionProperty("apply") match {
    case Some(command) => command
    case None => IRTK.deformImageCmd
  }

  /** Command used to deform a segmentation image */
  val deformLabelsCmd = getCmdOptionProperty("apply-nn") match {
    case Some(command) => command
    case None => IRTK.deformLabelsCmd
  }

  /** Command used to deform a vtkPointSet, i.e., vtkPolyData */
  val deformPointsCmd = getCmdOptionProperty("apply-vtk") match {
    case Some(command) => command
    case None => IRTK.deformPointsCmd
  }

  /** Command used to compute Jacobian determinant map */
  val jacCmd = getCmdOptionProperty("jacobian") match {
    case Some(command) => command
    case None => IRTK.jacCmd
  }
}
