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

import scala.sys.process._
import java.io.File


/**
 * Interface to IRTK executables
 */
object IRTK extends Configurable("irtk") {

  /** Directory containing executable binaries */
  val binDir = {
    val dir = getFileProperty("dir")
    if (!dir.exists()) throw new Exception(s"IRTK bin directory does not exist: $dir")
    val ireg = new File(dir, "ireg")
    if (!ireg.exists()) throw new Exception(s"Invalid IRTK version, missing ireg executable: $ireg")
    dir
  }

  /** Absolute path of binary IRTK executable */
  def binPath(name: String): String = new File(binDir, name).getPath

  /** Get IRTK command property */
  override def getCmdProperty(propName: String) = {
    val cmd = getCmdProperty(propName)
    Cmd(binPath(cmd.head)) ++ cmd.tail
  }

  /** Get IRTK command property */
  override def getCmdOptionProperty(propName: String) = getCmdOptionProperty(propName) match {
    case Some(cmd) => Some(Cmd(binPath(cmd.head)) ++ cmd.tail)
    case None => None
  }

  /** Maximum number of threads to be used by each command */
  val threads = getIntProperty("threads") match {
    case n if n <= 0 => Runtime.getRuntime.availableProcessors()
    case n if n >  0 => n
  }

  /** Default command to use for deforming images */
  val deformImageCmd = getCmdProperty("apply")

  /** Default command to use for deforming segmentation images */
  val deformLabelsCmd = getCmdProperty("apply-nn")

  /** Default command to use for computing Jacobian determinant */
  val jacCmd = getCmdProperty("jacobian")

  /** Version information */
  def version: String = "[0-9]+(\\.[0-9]+)?(\\.[0-9]+)?".r.findFirstIn(s"$binDir/ireg -version".!!).getOrElse("1.0")

  /** Git commit SHA */
  def revision: String = s"$binDir/ireg -revision".!!.trim

  /** List of used IRTK applications with arguments used for packing them using CARE */
  private def usedApplications = Seq("ireg", "dofprint", "dofinvert", "dofcombine", "ffdcompose", "transformation", "labelStats").map {
    name => Cmd(new File(binDir, name).getAbsolutePath, "-version")
  }

  /**
   * Pack all used IRTK executables into a single archive
   * @param dir Shared working directory
   * @return Resource files needed by tasks to execute commands
   */
  def resources(dir: File): Seq[File] = Bin.pack(dir, usedApplications: _*)

  /**
   * Pack all used IRTK executables into a single archive
   * @return Archive file needed by tasks to execute packed commands
   */
  def archive(): File = Bin.pack("IRTK-" + version + "-" + revision, usedApplications: _*)

  /** Execute IRTK command */
  protected def execute(command: String, args: Seq[String], log: Option[File] = None, errorOnReturnCode: Boolean = true): Int = {
    val cmd = Seq[String](FileUtil.join(binDir, command).getAbsolutePath) ++ args
    val cmdString = cmd.mkString("> \"", "\" \"", "\"\n")
    print('\n')
    val returnCode = log match {
      case Some(file) =>
        val logger = new TaskLogger(file)
        if (!logger.tee) println(cmdString)
        logger.out(cmdString)
        cmd ! logger
      case None =>
        println(cmdString)
        cmd.!
    }
    if (errorOnReturnCode && returnCode != 0) {
      throw new Exception(s"Error executing: ${cmd.head} return code was not 0 but $returnCode")
    }
    returnCode
  }

  /** Type of transformation file */
  def dofType(dof: File): String = {
    if (!dof.exists()) throw new Exception(s"Tranformation does not exist: ${dof.getAbsolutePath}")
    Seq[String](FileUtil.join(binDir, "dofprint").getAbsolutePath, dof.getAbsolutePath, "-type").!!.trim
  }

  /** Whether given transformation is linear */
  def isLinear(dof: File): Boolean = dofType(dof) match {
    case "irtkRigidTransformation" | "irtkAffineTransformation" | "irtkSimilarityTransformation" => true
    case _ => false
  }

  /** Whether given transformation is a FFD */
  def isFFD(dof: File): Boolean = !isLinear(dof)

  /** Invert transformation */
  def invert(dofIn: File, dofOut: File): Int = {
    if (!dofIn.exists()) throw new Exception(s"Input transformation does not exist: ${dofIn.getAbsolutePath}")
    dofOut.getAbsoluteFile.getParentFile.mkdirs()
    execute(if (isLinear(dofIn)) "dofinvert" else "ffdinvert", Seq(dofIn.getAbsolutePath, dofOut.getAbsolutePath))
  }

  /** Compose transformations: (dof2 o dof1) */
  def compose(dof1: File, dof2: File, dofOut: File, invert1: Boolean = false, invert2: Boolean = false): Int = {
    if (!dof1.exists()) throw new Exception(s"Input dof1 does not exist: ${dof1.getAbsolutePath}")
    if (!dof2.exists()) throw new Exception(s"Input dof2 does not exist: ${dof2.getAbsolutePath}")
    dofOut.getAbsoluteFile.getParentFile.mkdirs()
    if (isLinear(dof1) && isLinear(dof2)) {
      // Note: dof1 and dof2 arguments are swapped!
      val inv1 = if (invert1) Seq("-invert2") else Seq()
      val inv2 = if (invert2) Seq("-invert1") else Seq()
      execute("dofcombine", Seq(dof2.getAbsolutePath, dof1.getAbsolutePath, dofOut.getAbsolutePath) ++ inv1 ++ inv2)
    } else {
      // TODO: Write inverse FFD to temporary file or even better add -invert1/-invert2 options to ffdcompose
      if (invert1) throw new Exception(s"ffdcompose does not support inversion of dof1 (${dofType(dof1)})")
      if (invert2) throw new Exception(s"ffdcompose does not support inversion of dof2 (${dofType(dof2)})")
      execute("ffdcompose", Seq(dof1.getAbsolutePath, dof2.getAbsolutePath, dofOut.getAbsolutePath))
    }
  }

  /** Compute image transformation using ireg */
  def ireg(target: File, source: File, dofin: Option[File], dofout: File, log: Option[File], params: (String, Any)*): Int = {
    if (!target.exists) throw new Exception(s"Target image does not exist: ${target.getAbsolutePath}")
    if (!source.exists) throw new Exception(s"Source image does not exist: ${source.getAbsolutePath}")
    dofout.getAbsoluteFile.getParentFile.mkdirs()
    val din = dofin match {
      case Some(file) =>
        if (!file.exists) throw new Exception(s"Initial transformation does not exist: ${file.getAbsolutePath}")
        Seq("-dofin", file.getAbsolutePath)
      case None => Seq()
    }
    val dout = Seq("-dofout", dofout.getAbsolutePath)
    val opts = params flatMap {
      case (k, v) if k == "Verbosity" => Seq("-verbose", v.toString)
      case (k, v) if k == "No. of threads" => Seq("-threads", v.toString)
      case (k, v) => Seq("-par", s"$k = $v")
      case _ => None
    }
    execute("ireg", Seq(target.getAbsolutePath, source.getAbsolutePath, "-threads", threads.toString) ++ din ++ dout ++ opts, log)
  }

  /**
   * Transform/resample image
   *
   * @param source Image to be transformed.
   * @param output Transformed output image.
   * @param dofin Transformation from target to source.
   * @param interpolation Interpolation method to use, e.g., "NN", "Linear", "Cubic".
   * @param target Fixed target image.
   * @param matchInputType Whether to match the type of the input source instead of the target.
   *
   * @return Zero exit code upon success.
   */
  def transform(source: File, output: File, dofin: File,
                interpolation: String = "Linear",
                target: Option[File] = None,
                matchInputType: Boolean = false): Int = {
    if (!source.exists) throw new Exception(s"Source image does not exist: ${source.getAbsolutePath}")
    if (!dofin.exists) throw new Exception(s"Transformation does not exist: ${dofin.getAbsolutePath}")
    val opt1 = Seq("-" + interpolation.toLowerCase)
    val opt2 = target match {
      case Some(image) =>
        if (!image.exists) throw new Exception(s"Target image does not exist: ${image.getAbsolutePath}")
        Seq("-target", image.getAbsolutePath)
      case None => Seq()
    }
    val opt3 = if (matchInputType) Seq("-matchInputType") else Seq()
    output.getAbsoluteFile.getParentFile.mkdirs()
    execute("transformation", Seq(source.getAbsolutePath, output.getAbsolutePath,
      "-dofin", dofin.getAbsolutePath) ++ opt1 ++ opt2 ++ opt3)
  }

  /**
   * Compute overlap statistics for each label
   * @param a Segmentation of image A.
   * @param b Segmentation of image B.
   * @param labels Labels for which to compute overlap statistics. If None specified, the overlap is
   *               computed for all non-zero labels found in the segmentations.
   * @return A map from label number to overlap measurement vector:
   *         0: Jaccard similarity index (JSI)
   *         1: Dice similarity coefficient (DSC)
   */
  def labelStats(a: File, b: File, labels: Option[Set[Int]] = None): Map[Int, Array[Double]] = {
    var label2stats = labels match {
      case Some(set) => set.map(l => l -> Array.fill(2)(.0)).toMap
      case None      => scala.collection.mutable.Map[Int, Array[Double]]()
    }
    Seq("labelStats", a.getAbsolutePath, b.getAbsolutePath).lineStream.foreach(line => {
      val v = line.split(',')
      val l = v(0).toInt
      if (labels match {
        case Some(set) => set.contains(l)
        case None      => true
      }) label2stats += l -> Array(v(4).toDouble, v(5).toDouble)
    })
    label2stats.toMap
  }
}
