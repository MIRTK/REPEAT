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

import java.nio.file.{Paths, Files}


/**
 * Information of configured registration methods/implementations
 */
object Registration extends Configurable("registration") {

  /** Runtime measurements */
  lazy val times = List("User", "System", "Total", "Real")

  /** Names of available/configured registrations */
  val names = getPropertyKeySet(".registration")

  /** Names of registrations to evaluate */
  val use = {
    val methods = getStringListOptionProperty("use").getOrElse(names.toList).distinct
    val unknown = methods.filter(!names.contains(_))
    if (unknown.nonEmpty) throw new Exception("Cannot evaluate unknown registration: " + unknown.mkString(", "))
    methods
  }

  /** File path template of common directory containing CSV files with registration parameters */
  val parDir = getPathProperty("params.dir")

  /** Parameters table name template */
  val parName = getStringProperty("params.name")

  /** Get info object for named registration */
  def apply(name: String) = new Registration(name)
}


/**
 * Configuration of named registration command
 */
class Registration(val id: String) extends Configurable("registration." + id) {

  /** Whether this registration is symmetric */
  val isSym = getBooleanOptionProperty("symmetric") getOrElse false

  /** File path template of CSV file with command parameters */
  val parCsv = expand(getStringOptionProperty("params") match {
    case Some(name) =>
      val testPath = Paths.get(expand(name, Map("setId" -> Dataset.use.head, "regId" -> id)))
      if (Files.exists(testPath)) Paths.get(name).toAbsolutePath
      else Registration.parDir.resolve(name)
    case None => Registration.parDir.resolve(Registration.parName)
  }, Map("regId" -> id))

  /** Get file path of registration parameters table for a specific dataset */
  def parCsvPath(setId: String) = expand(parCsv, Map("setId" -> setId))

  /** Template configuration file content for registration command */
  val config = getStringOptionProperty("config")

  /** Optional command used to convert affine transformation from IRTK format to required input format */
  val dof2affCmd = getCmdOptionProperty("dof2aff")

  /** Directory of registration log files */
  val logDir = Workspace.logDir.resolve("${regId}-${parId}")

  /** File name suffix for Jacobian determinant map */
  val jacSuf = getStringOptionProperty("suffix.jac") getOrElse Suffix.img

  /** File name suffix for converted affine input transformation */
  val affSuf = getStringOptionProperty("suffix.aff") getOrElse Suffix.dof

  /** File name suffix for output transformation */
  val phiSuf = getStringOptionProperty("suffix.phi") getOrElse Suffix.dof

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
    case Some(_) => Suffix.dof
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
