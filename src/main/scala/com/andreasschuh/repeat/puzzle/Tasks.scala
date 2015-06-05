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

package com.andreasschuh.repeat.puzzle

import java.io.File
import java.nio.file.{Path, Paths}
import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.core.workflow.mole.Capsule
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.{Switch, Case}

import com.andreasschuh.repeat.core.{Environment => Env, _}


/**
 * Tasks factory
 */
object Tasks {
  def apply(reg: Registration, puzzleName: String = "UnknownPuzzle") = new Tasks(reg, puzzleName)
}

/**
 * Tasks factory
 *
 * @param reg Registration info.
 */
class Tasks(reg: Registration, puzzleName: String) {

  // Import common variable prototypes
  import Variables.{go, regId, parVal, parIdx, parId, tgtId, srcId}

  // Info about workflow stream for inclusion in status messages
  lazy val regInfo = s"{regId=$${${regId.name}}}"
  lazy val regAndParInfo = s"{regId=$${${regId.name}}, parId=$${${parId.name}}}"
  lazy val regParTgtAndSrcInfo = s"{regId=$${${regId.name}}, parId=$${${parId.name}}, tgtId=$${${tgtId.name}}, srcId=$${${srcId.name}}}"

  /** Default capsule at start of workflow puzzle without parent dependencies */
  def start =
    Capsule(
      EmptyTask() set (
        outputs += go,
        go      := true
      )
    )

  /** Demux aggregated results by taking head element only */
  def getHead(taskName: String, input: Prototype[_]*) = {
    val inputNames = input.toSeq.map(_.name)
    val task =
      ScalaTask(inputNames.map(name => s"val $name = input.$name.head").mkString("\n")) set (
        name := s"$puzzleName(${reg.id}).$taskName.getHead(${inputNames.mkString(",")})"
      )
    input.foreach(p => {
      task.addInput(p.toArray)
      task.addOutput(p)
    })
    Capsule(task)
  }

  /** Set regId at start of workflow puzzle */
  def putRegId =
    Capsule(
      EmptyTask() set (
        name    := s"$puzzleName(${reg.id}).setRegId",
        inputs  += go,
        outputs += regId,
        regId   := reg.id
      )
    )

  /** Set parId either to ID column entry or parIdx (i.e., CSV row index) */
  def putParId =
    Capsule(
      ScalaTask(
        """
          | val parId  = input.parVal.getOrElse("ID", f"$parIdx%02d")
          | val parVal = input.parVal - "ID"
        """.stripMargin
      ) set (
        name    := s"$puzzleName(${reg.id}).putParId",
        inputs  += (regId, parIdx, parVal),
        outputs += (regId, parId,  parVal)
      )
    )

  /** Get full path of registration result table */
  def resultTable(name: String) = Paths.get(reg.resDir.getAbsolutePath, name + ".csv").toString

  /** Get full path of registration result summary table */
  def summaryTable(name: String) = Paths.get(reg.sumDir.getAbsolutePath, name + ".csv").toString

  /** Delete table with summary results which can be recomputed from individual result tables */
  def deleteTable(path: String, enabled: Boolean = true) = {
    val task =
      if (enabled)
        ScalaTask(
          s"""
            | val table = Paths.get(s"$path")
            | if (Files.exists(table)) {
            |   Files.delete(table)
            |   println(s"$${DONE}Delete $${table.getFileName} for $regInfo")
            | }
          """.stripMargin
        ) set (
          name    := s"$puzzleName(${reg.id}).deleteTable(${FileUtil.getName(path)})",
          imports += ("java.nio.file.{Paths, Files}","com.andreasschuh.repeat.core.Prefix.DONE"),
          inputs  += regId,
          outputs += regId
        )
      else
        EmptyTask() set (
          name    := s"$puzzleName(${reg.id}).keepTable(${FileUtil.getName(path)})",
          inputs  += regId,
          outputs += regId
        )
    Capsule(task)
  }

  /**
  Get path of backup table */
  def backupTablePath(path: String) = {
    val p = Paths.get(path)
    p.getParent.resolve(FileUtil.hidden(p.getFileName.toString)).toString
  }

  /** Make copy of previous result tables and merge them with previously copied results to ensure none are lost */
  def backupTable(path: String, enabled: Boolean = true) = {
    val task =
      if (enabled)
        ScalaTask(
          s"""
            | val from = new File(s"$path")
            | val to   = new File(s"${backupTablePath(path)}")
            | if (from.exists) {
            |   val l1 = if (to.exists) fromFile(to).getLines().toList.drop(1) else List[String]()
            |   val l2 = fromFile(from).getLines().toList
            |   val fw = new FileWriter(to)
            |   try {
            |     fw.write(l2.head + "\\n")
            |     val l: List[String] = (l1 ::: l2.tail).groupBy( _.split(",").take(2).mkString(",") ).map(_._2.last)(breakOut)
            |     l.sortBy( _.split(",").take(2).mkString(",") ).foreach( row => fw.write(row + "\\n") )
            |   }
            |   finally fw.close()
            |   java.nio.file.Files.delete(from.toPath)
            |   println(s"$${DONE}Backup $${from.getName} for $regAndParInfo")
            | }
          """.stripMargin
        ) set (
          name    := s"$puzzleName(${reg.id}).backupTable(${FileUtil.getName(path)})",
          imports += ("java.io.{File, FileWriter}", "scala.io.Source.fromFile", "scala.collection.breakOut"),
          imports += "com.andreasschuh.repeat.core.Prefix.DONE",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
      else
        EmptyTask() set (
          name    := s"$puzzleName(${reg.id}).keepTable(${FileUtil.getName(path)})",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
    Capsule(task, strainer = true)
  }

  /** Read previous result from backup table to save re-computation if nothing changed */
  def readFromTable(path: String, columns: Seq[_], values: Prototype[Array[Double]], enabled: Boolean = true) =
    Capsule(
      ScalaTask(
        s"""
          | val enabled = $enabled
          | val columns = Array[String](${if (columns.isEmpty) "" else "\"" + columns.mkString("\", \"") + "\""})
          |
          | val ${values.name} =
          |   if (enabled)
          |     try {
          |       val file = new File(s"${backupTablePath(path)}")
          |       val rows = fromFile(file).getLines().toList
          |       if (!rows.head.startsWith("Target,Source,")) throw new Exception("Invalid table " + file.getPath)
          |       val hdr = rows.head.split(",").zipWithIndex.toMap
          |       val row = rows.tail.view.filter(_.startsWith(s"$${${tgtId.name}},$${${srcId.name}},")).last.split(",")
          |       if (row.size != hdr.size) throw new Exception("Invalid table " + file.getPath)
          |       val values = columns.map(name => row(hdr(name)).toDouble)
          |       println(HAVE + s"${values.name.capitalize} for $regParTgtAndSrcInfo")
          |       values
          |     }
          |     catch {
          |       case _: Exception => Array[Double]()
          |     }
          |   else Array[Double]()
        """.stripMargin
      ) set (name    := s"$puzzleName(${reg.id}).read(${values.name})",
        imports += ("java.io.File","scala.io.Source.fromFile", "Double.NaN", "com.andreasschuh.repeat.core.Prefix.HAVE"),
        inputs  += (regId, parId, tgtId, srcId),
        outputs += (regId, parId, tgtId, srcId, values)
      ),
      strainer = true
    )

  /** Calculate mean of values over all registration results computed with a fixed set of parameters */
  def getMean(result: Prototype[Array[Double]], mean: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(
        s"""
          | val ncols = ${result.name}.length
          | val valid = ncols > 0 && !${result.name}.exists(_.isEmpty)
          | val ${mean.name} = if (valid) ${result.name}.transpose.map(_.sum / ncols) else Array[Double]()
        """.stripMargin
      ) set (
        name    := s"$puzzleName(${reg.id}).getMean(${result.name})",
        inputs  += result.toArray,
        outputs += mean
      ),
      strainer = true
    )

  /** Calculate standard deviation of values over all registration results computed with a fixed set of parameters */
  def getSD(result: Prototype[Array[Double]], sigma: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(
        s"""
          | val ncols = ${result.name}.length
          | val valid = ncols > 0 && !${result.name}.exists(_.isEmpty)
          | val ${sigma.name} =
          |   if (valid) {
          |     val mean  = ${result.name}.transpose.map(_.sum / ncols)
          |     val mean2 = ${result.name}.transpose.map(_.map(pow(_, 2)).sum / ncols)
          |     mean2.zipWithIndex.map {
          |       case (m2, i) =>
          |         val variance = m2 - pow(mean(i), 2)
          |         if (variance > .0) sqrt(variance) else .0
          |     }
          |   }
          |   else Array[Double]()
        """.stripMargin
      ) set (
        name    := s"$puzzleName(${reg.id}).getSD(${result.name})",
        imports += "scala.math.{pow, sqrt}",
        inputs  += result.toArray,
        outputs += sigma
      ),
      strainer = true
    )

  /** Calculate mean and standard deviation of values over all registration results computed with a fixed set of parameters */
  def getMeanAndSD(result: Prototype[Array[Double]], mean: Prototype[Array[Double]], sigma: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(
        s"""
          | val ncols = ${result.name}.length
          | val valid = ncols > 0 && !${result.name}.exists(_.isEmpty)
          | val ${mean.name} = if (valid) ${result.name}.transpose.map(_.sum / ncols) else Array[Double]()
          | val ${sigma.name} =
          |   if (valid) {
          |     ${result.name}.transpose.map(_.map(pow(_, 2)).sum / ncols).map(_.zipWithIndex.map {
          |       case (mean2, i) =>
          |         val variance = mean2 - pow(mean(i), 2)
          |         if (variance > 0) sqrt(variance) else .0
          |     })
          |   else Array[Double]()
        """.stripMargin
      ) set (
        name    := s"$puzzleName(${reg.id}).getMeanAndSD(${result.name})",
        imports += "scala.math.{pow, sqrt}",
        inputs  += result.toArray,
        outputs += (mean, sigma)
      ),
      strainer = true
    )

  /** Write individual registration result to CSV table */
  def saveToTable(path: String, header: Seq[_], result: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(s"""println(SAVE + s"${result.name.capitalize} for $regParTgtAndSrcInfo") """) set (
        name    := s"$puzzleName(${reg.id}).saveToTable(${result.name})",
        imports += "com.andreasschuh.repeat.core.Prefix.SAVE",
        inputs  += (regId, parId, tgtId, srcId, result),
        outputs += (regId, parId, tgtId, srcId, result)
      )
    ) hook (
      AppendToCSVFileHook(path, tgtId, srcId, result) set (
        csvHeader := "Target,Source," + header.mkString(","),
        singleRow := true
      )
    )

  /** Write mean values calculated over all registration results computed with a fixed set of parameters to CSV table */
  def saveToSummary(path: String, header: Seq[_], mean: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(s"""println(SAVE + s"${mean.name.capitalize} for $regAndParInfo") """) set (
        name    := s"$puzzleName(${reg.id}).saveToSummary(${mean.name})",
        imports += "com.andreasschuh.repeat.core.Prefix.SAVE",
        inputs  += (regId, parId, mean),
        outputs += (regId, parId, mean)
      )
    ) hook (
      AppendToCSVFileHook(path, regId, parId, mean) set (
        csvHeader := "Registration,Parameters," + header.mkString(","),
        singleRow := true
      )
    )

  /** Finalize result table, appending non-overwritten previous results again and sorting the final table */
  def finalizeTable(path: String, enabled: Boolean = true) = {
    val task =
      if (enabled)
        ScalaTask(
          s"""
            | val from = new java.io.File(s"${backupTablePath(path)}")
            | val to   = new java.io.File(s"$path")
            | if (from.exists) {
            |   val l1 = fromFile(from).getLines().toList
            |   val l2 = if (to.exists) fromFile(to).getLines().toList.tail else List[String]()
            |   val fw = new java.io.FileWriter(to)
            |   try {
            |     fw.write(l1.head + "\\n")
            |     val l: List[String] = (l1.tail ::: l2).groupBy( _.split(",").take(2).mkString(",") ).map(_._2.last)(breakOut)
            |     l.sortBy( _.split(",").take(2).mkString(",") ).foreach( row => fw.write(row + "\\n") )
            |   }
            |   finally fw.close()
            |   java.nio.file.Files.delete(from.toPath)
            |   println(DONE + s"Finalize $${to.getName} for $regAndParInfo")
            | }
          """.stripMargin
        ) set (
          name    := s"$puzzleName(${reg.id}).finalizeTable(${FileUtil.getName(path)}})",
          imports += ("scala.io.Source.fromFile", "scala.collection.breakOut","com.andreasschuh.repeat.core.Prefix.DONE"),
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
      else
        EmptyTask() set (
          name    := s"$puzzleName(${reg.id}).keepTable(${FileUtil.getName(path)}})",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
    Capsule(task, strainer = true)
  }

  /*

  /** Deform image using the transformation obtained by a registration */
  def deformImage(outDof: Option[Prototype[File]] = None, name: String = "UnknownPuzzle") = {
    val deformCmd = Val[Cmd]
    val task =
      ScalaTask(
        s"""
          | val tgtImg = Paths.get(s"$imgDir", "$imgPre" + tgtId + "$imgSuf")
          | val srcImg = Paths.get(s"$imgDir", "$imgPre" + imgId + "$imgSuf")
          | val outImg = new File(workDir, imgId + "-" + refId + "$imgExt")
        """.stripMargin + (if (outDof == None)
        s"""
          | val outDof = Paths.get(s"${reg.dofDir}", "$dofPre" + refId + "," + imgId + "${reg.dofSuf}")
        """.stripMargin else "") +
        s"""
          | val args = Map(
          |   "target" -> refImg.toString,
          |   "source" -> srcImg.toString,
          |   "phi"    -> outDof.toString,
          |   "out"    -> outImg.toString
          | )
          | val cmd = Cmd(deformImg, args)
          | if (0 != cmd.!) {
          |   throw new Exception("Image transformation command returned non-zero exit code: " + Cmd.toString(cmd))
          | }
        """.stripMargin
      ) set (
        name      := s"UnknownPuzzle(${reg.id}).deformImage",
        imports   += ("java.io.File", "java.nio.file.{Paths, Files}", "com.andreasschuh.repeat.core._"),
        inputs    += (regId, parId, refId, imgId),
        outputs   += (regId, parId, refId, imgId, outImg),
        deformCmd := reg.deformImageCmd
      )
    task
  }

*/
}
