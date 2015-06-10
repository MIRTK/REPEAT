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

import java.io.{FileWriter, File}
import java.nio.file.{Paths, Files}
import scala.sys.process._


/**
 * Information of configured registration methods/implementations
 */
object Registration extends Configurable("registration") {

  /** Runtime measurements */
  lazy val times = List("User", "System", "Total", "Real")

  /** Names of available/configured registrations */
  val names = getPropertyKeySet(".registration") - "params"

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
      val testPath = Paths.get(expand(name, Map(("setId", DataSet.use.head), ("regId", id))))
      if (Files.exists(testPath)) Paths.get(name).toAbsolutePath
      else Registration.parDir.resolve(name)
    case None => Registration.parDir.resolve(Registration.parName)
  }, Map(("regId", id)))

  /** Get file path of registration parameters table for a specific dataset */
  def parCsvPath(setId: String) = expand(parCsv, Map("setId" -> setId))

  /** Template configuration file content for registration command */
  val config = getStringOptionProperty("config")

  /** Directory of registration log files */
  val logDir = WorkSpace.logDir.resolve("${regId}-${parId}")

  /** File name suffix for Jacobian determinant map */
  val jacSuf = getStringOptionProperty("suffix.jac") getOrElse Suffix.img

  /** File name suffix for converted affine input transformation */
  val affSuf = getStringOptionProperty("suffix.aff") getOrElse Suffix.dof

  /** File name suffix for output transformation */
  val phiSuf = getStringOptionProperty("suffix.phi") getOrElse Suffix.dof

  /** Command to run image registration */
  val command = getCmdProperty("command")

  /** Execution environment for registration command */
  val env = Environment(name    = getStringOptionProperty("environment"),
                        memory  = getIntOptionProperty("memory"),
                        nodes   = getIntOptionProperty("nodes"),
                        threads = getIntOptionProperty("threads"))

  /** Optional command used to convert affine transformation from IRTK format to required input format */
  val dof2affCmd = getCmdOptionProperty("dof2aff")

  /** Optional command which can be used to convert output transformation to IRTK format */
  val phi2dofCmd = getCmdOptionProperty("phi2dof")

  /** File name suffix of (converted) output transformation */
  val dofSuf = if (phi2dofCmd == None) phiSuf else Suffix.dof

  /** Command used to deform an image */
  val deformImageCmd = getCmdOptionProperty("apply") getOrElse IRTK.deformImageCmd

  /** Command used to deform a segmentation image */
  val deformLabelsCmd = getCmdOptionProperty("apply-nn") getOrElse IRTK.deformLabelsCmd

  /** Command used to deform a vtkPointSet, i.e., vtkPolyData */
  val deformPointsCmd = getCmdOptionProperty("apply-vtk") getOrElse IRTK.deformPointsCmd

  /** Command used to compute Jacobian determinant map */
  val jacCmd = getCmdOptionProperty("jacobian") getOrElse IRTK.jacCmd

  /**
   * Register given pair of images
   *
   * @param tgtId  ID of target image.
   * @param tgtImg Target image.
   * @param srcId  ID of source image.
   * @param srcImg Source image.
   * @param parMap Parameter values of registration command.
   * @param iniDof Affine input transformation.
   * @param outLog Redirect registration output to log file.
   * @param outDir Output directory.
   * @param tmpDir Directory for temporary output files.
   *
   * @return Tuple of output transformation and array with logged runtime measurements (empty if none found in command output).
   */
  def apply(tgtId: String, tgtImg: File, srcId: String, srcImg: File, parMap: Option[Map[String, _]],
            iniDof: Option[File] = None, outLog: Option[File] = None, outDir: Option[File] = None, tmpDir: Option[File] = None) = {

    // Use tuples instead of -> to initialize command argument maps to avoid inliner warnings
    // (cf. https://issues.scala-lang.org/browse/SI-6723)

    val params = parMap.getOrElse(Map[String, String]()).mapValues(_.toString)
    val idArgs = Map(("regId", id), ("parId", params("ID")))

    val _tmpDir = tmpDir.getOrElse(new File("/tmp"))
    val _outDir = outDir.getOrElse(new File("."))

    if (!_tmpDir.isDirectory) Files.createDirectories(_tmpDir.toPath)
    if (!_outDir.isDirectory) Files.createDirectories(_outDir.toPath)

    val affDof = new File(_tmpDir, tgtId + "," + srcId + "-aff" + affSuf)
    val phiDof = new File(_tmpDir, tgtId + "," + srcId + "-phi" + phiSuf)
    val outDof = new File(_outDir, tgtId + "," + srcId + "-phi" + dofSuf)

    val log = new TaskLogger(outLog)

    val regCfg = new File(_tmpDir, "reg.cfg")
    val cfg = expand(config.getOrElse("").stripMargin.trim, params)

    if (cfg.length > 0) {
      val out = new FileWriter(regCfg)
      out.write(cfg + "\n")
      out.close()
      val label = "Configuration"
      log.out(("-" * (40 - label.length / 2)) + label + ("-" * (40 - (label.length + 1) / 2)))
      log.out(cfg + "\n" + ("-" * 80) + "\n")
    }

    // Make initial guess
    val _iniDof = iniDof getOrElse {
      val iniDof = new File(_tmpDir, tgtId + "," + srcId + Suffix.dof)
      val cmd = Cmd(IRTK.binPath("dofinit"), tgtImg, srcImg, iniDof)
      if (cmd.run().exitValue() != 0) {
        throw new Exception("Transformation initialization command returned non-zero exit code: " + Cmd.toString(cmd))
      }
      iniDof
    }

    // Convert input affine transformation from IRTK to required file format
    dof2affCmd match {
      case Some(dof2aff) =>
        val args = idArgs ++ Map(
          ("in"    , _iniDof),
          ("dof"   , _iniDof),
          ("dofin" , _iniDof),
          ("aff"   , affDof),
          ("out"   , affDof)
        )
        val cmd = Cmd(dof2aff, args)
        log.out("\nREPEAT> " + Cmd.toString(cmd) + "\n")
        if (cmd.run(log).exitValue() != 0) {
          log.close()
          if (!log.tee) log.printToErr()
          throw new Exception("Affine transformation conversion command returned non-zero exit code: " + Cmd.toString(cmd))
        }
      case None =>
        if (affDof != _iniDof) Files.move(_iniDof.toPath, affDof.toPath)
    }

    // Run registration command
    val args = params ++ idArgs ++ Map(
      ("target" , tgtImg),
      ("source" , srcImg),
      ("aff"    , affDof),
      ("phi"    , phiDof),
      ("config" , regCfg)
    )
    val cmd = Cmd("/usr/bin/time", "-p") ++ Cmd(command, args)
    log.out("\nREPEAT> " + Cmd.toString(cmd) + "\n")
    log.resetTime()
    if (cmd.run(log).exitValue() != 0) {
      log.close()
      if (!log.tee) log.printToErr()
      throw new Exception("Registration returned non-zero exit code: " + Cmd.toString(cmd))
    }

    val runTime = if (log.noTime) Array[Double]() else {
      val t = log.time
      val unit = Time.units match {
        case Time.Seconds => 1.0
        case Time.Minutes => 1.0 / 60.0
        case Time.Hours => 1.0 / 3600.0
      }
      Time.modes.map(_ match {
        case Time.User => t(0) * unit
        case Time.System => t(1) * unit
        case Time.Total => (t(0) + t(1)) * unit
        case Time.Real => t(2) * unit
      }).toArray
    }

    // Convert output transformation to IRTK format (optional)
    phi2dofCmd match {
      case Some(phi2dof) =>
        val args = idArgs ++ Map(
          ("phi", phiDof),
          ("dof", outDof)
        )
        val cmd = Cmd(phi2dof, args)
        log.out("\nREPEAT> " + Cmd.toString(cmd) + "\n")
        if (cmd.run(log).exitValue != 0) {
          log.close()
          if (!log.tee) log.printToErr()
          throw new Exception("Failed to convert output transformation: " + Cmd.toString(cmd))
        }
      case None => if (outDof != phiDof) Files.move(phiDof.toPath, outDof.toPath)
    }

    log.close()
    (outDof, runTime)
  }
}
