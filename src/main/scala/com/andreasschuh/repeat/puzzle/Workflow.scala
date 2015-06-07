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
import org.openmole.core.workflow.puzzle.Puzzle
import org.openmole.core.workflow.sampling._
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.domain.collection._
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern._

import com.andreasschuh.repeat.core._


/**
 * Workflow puzzle
 */
abstract class Workflow(start: Option[Capsule] = None) {

  // Name of workflow puzzle for identification in formal validation errors
  protected val wf = this.getClass.getSimpleName

  protected lazy val dataSet   = Val[Dataset]      ///< Dataset info object
  protected lazy val dataSpace = Val[DatasetWorkspace]
  protected lazy val evalSpace = Val[EvaluationWorkspace]
  protected lazy val reg       = Val[Registration] ///< Registration info object

  // Commonly used workflow variable prototypes
  protected lazy val setId  = Val[String]               ///< Dataset name/ID
  protected lazy val regId  = Val[String]               ///< Registration name/ID
  protected lazy val parVal = Val[Map[String, String]]  ///< Registration parameter name/value map
  protected lazy val parIdx = Val[Int]                  ///< Row index of parameter set
  protected lazy val parId  = Val[String]               ///< Parameter set ID
  protected lazy val refId  = Val[String]               ///< Reference image ID
  protected lazy val tgtId  = Val[String]               ///< Target image ID (i.e., fixed  image in pairwise registration)
  protected lazy val srcId  = Val[String]               ///< Source image ID (i.e., moving image in pairwise registration)
  protected lazy val imgId  = Val[String]               ///< An image ID

  // Info about workflow stream for inclusion in status messages
  protected lazy val setInfo             = "{setId=${setId}}"
  protected lazy val regInfo             = "{setId=${setId}, regId=${regId}}"
  protected lazy val regAndParInfo       = "{setId=${setId}, regId=${regId}, parId=${parId}}"
  protected lazy val regParTgtAndSrcInfo = "{setId=${setId}, regId=${regId}, parId=${parId}, tgtId=${tgtId}, srcId=${srcId}}"

  /** Get OpenMOLE Puzzle corresponding to this workflow */
  def toPuzzle: Puzzle

  /** Empty strainer capsule */
  protected def nop(taskName: String) = Capsule(EmptyTask() set (name := wf + "." + taskName), strainer = true)

  /** Capsule at start of workflow puzzle */
  val begin = start getOrElse nop("begin")

  /** First slot of workflow puzzle */
  protected lazy val first = Slot(begin)

  /** Capsule at the end of workflow puzzle */
  val end = nop("end")

  /** Explore datasets to be used, usually the start of any workflow puzzle */
  protected def forEachDataSet =
    Capsule(
      ExplorationTask(setId in Dataset.use) set (
        name := wf + ".forEachDataSet"
      ),
      strainer = true
    )

  /** Set setId at start of workflow puzzle specific to a single dataset */
  protected def putDataSetId(id: String) =
    Capsule(
      EmptyTask() set (
        name    := wf + ".putDataSetId",
        outputs += setId,
        setId   := id
      ),
      strainer = true
    )

  /** Set setId at start of workflow puzzle specific to a single dataset */
  protected def putDataSet(d: Dataset) =
    Capsule(
      EmptyTask() set (
        name    := wf + ".putDataSet",
        outputs += dataSet,
        dataSet := d
      ),
      strainer = true
    )

  /** Inject dataset info object into workflow */
  protected def getDataSet =
    Capsule(
      ScalaTask(
        s"""
          | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
          | val dataSet = Dataset(setId)
        """.stripMargin
      ) set (
        name        := wf +".getDataSet",
        imports     += "com.andreasschuh.repeat.core.{Config, Dataset}",
        usedClasses += (Config.getClass, Dataset.getClass),
        inputs      += setId,
        outputs     += (setId, dataSet)
      ),
      strainer = true
    )

  /** Inject dataset workspace info object into workflow */
  protected def getDataSpace = {
    Capsule(
      ScalaTask(
        s"""
          | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
          | val dataSpace = DatasetWorkspace(Dataset(setId))
        """.stripMargin
      ) set (
        name        := wf +".getDataSpace",
        imports     += "com.andreasschuh.repeat.core.{Config, Dataset, DatasetWorkspace}",
        usedClasses += (Config.getClass, Dataset.getClass, classOf[DatasetWorkspace]),
        inputs      += setId,
        outputs     += (setId, dataSpace)
      ),
      strainer = true
    )
  }

  /** Explore registrations to be evaluated */
  protected def forEachReg =
    Capsule(
      ExplorationTask(regId in Registration.use) set (
        name := wf + ".forEachReg"
      ),
      strainer = true
    )

  /** Set regId at start of workflow puzzle specific to a single registration */
  protected def putRegId(reg: Registration) =
    Capsule(
      EmptyTask() set (
        name    := wf + ".putRegId",
        outputs += regId,
        regId   := reg.id
      ),
      strainer = true
    )

  /** Inject registration info object into workflow */
  protected def getReg =
    Capsule(
      ScalaTask(
        s"""
          | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}"))
          | val reg = Registration(regId)
        """.stripMargin
      ) set (
        name        := wf + ".getRegInfo",
        imports     += "com.andreasschuh.repeat.core.{Config, Dataset}",
        usedClasses += (Config.getClass, Dataset.getClass),
        inputs      += regId,
        outputs     += (regId, reg)
      ),
      strainer = true
    )

  /** Explore parameter set IDs of a specific registration */
  protected def forEachParId = {
    // TODO: Unlike forEachPar, this requires an "ID" column to be present
    val sampling = CSVSampling("${reg.parCsv}")
    sampling.addColumn("ID", parId)
    Capsule(
      ExplorationTask(sampling) set (
        name   := wf + ".forEachParId",
        inputs += reg
      ),
      strainer = true
    )
  }

  /** Explore parameter sets of a specific registration */
  protected def forEachPar =
    Capsule(
      ExplorationTask(CSVToMapSampling("${reg.parCsv}", parVal) zipWithIndex parIdx) set (
        name   := wf + ".forEachPar",
        inputs += reg
      ),
      strainer = true
    )

  /**
   * Set parId either to ID column entry of parameters table or
   * the corresponding parIdx (i.e., CSV row index) if no such column exists
   */
  protected def getParId =
    Capsule(
      ScalaTask(
        """
          | val parId  = input.parVal.getOrElse("ID", f"$parIdx%02d")
          | val parVal = input.parVal - "ID"
        """.stripMargin
      ) set (
        name    := wf + ".putParId",
        inputs  += (parIdx, parVal),
        outputs += (parId,  parVal)
      ),
      strainer = true
    )

  /** Explore each image of dataset */
  private def forEachImgInDataSet(p: Prototype[String]) = {
    val sampling = CSVSampling("${dataSpace.imgCsv}")
    sampling.addColumn("Image ID", p)
    Capsule(
      ExplorationTask(sampling) set(
        name   := wf + ".forEachImgInDataSet",
        inputs += dataSpace
      ),
      strainer = true
    )
  }

  /** Explore each image of dataset */
  protected def forEachImg = forEachImgInDataSet(imgId)

  /** Explore each reference image of dataset */
  protected def forEachImgAsRef = forEachImgInDataSet(refId)

  /** Copy file */
  protected def copy(from: Prototype[Path], to: Prototype[Path]) =
    Capsule(
      ScalaTask(
        s"""
          | if (${to.name} != ${from.name} &&
          |     (!Files.exists(${to.name}) || ${to.name}.toFile.lastModified < ${from.name}.toFile.lastModified)) {
          |   val outDir = ${to.name}.getParent
          |   if (outDir != null) Files.createDirectories(outDir)
          |   Files.deleteIfExists(${to.name})
          |   Files.copy(${from.name}, ${to.name})
          | }
        """.stripMargin
      ) set (
        name    := wf + s".copy(${from.name}, ${to.name})",
        imports += "java.nio.file.Files",
        inputs  += (from, to),
        outputs += to
      ),
      strainer = true
    )

  /** Demux aggregated results by taking head element only */
  protected def getHead(taskName: String, input: Prototype[_]*) = {
    val inputNames = input.toSeq.map(_.name)
    val task =
      ScalaTask(inputNames.map(name => s"val $name = input.$name.head").mkString("\n")) set (
        name := wf + s".$taskName.getHead(${inputNames.mkString(",")})"
      )
    input.foreach(p => {
      task.addInput(p.toArray)
      task.addOutput(p)
    })
    Capsule(task)
  }

  /** Get full path of registration result table */
  protected def resultTable(dir: File, name: String) = Paths.get(dir.getAbsolutePath, name + Suffix.csv).toString

  /** Get full path of registration result summary table */
  protected def summaryTable(dir: File, name: String) = Paths.get(dir.getAbsolutePath, name + Suffix.csv).toString

  /** Delete table with summary results which can be recomputed from individual result tables */
  protected def deleteTable(path: String, enabled: Boolean = true) = {
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
          name    := wf + s".deleteTable(${FileUtil.getName(path)})",
          imports += ("java.nio.file.{Paths, Files}","com.andreasschuh.repeat.core.Prefix.DONE"),
          inputs  += regId,
          outputs += regId
        )
      else
        EmptyTask() set (
          name    := wf + s".keepTable(${FileUtil.getName(path)})",
          inputs  += regId,
          outputs += regId
        )
    Capsule(task)
  }

  /** Get path of backup table */
  protected def backupTablePath(path: String) = {
    val p = Paths.get(path)
    p.getParent.resolve(FileUtil.hidden(p.getFileName.toString)).toString
  }

  /** Make copy of previous result tables and merge them with previously copied results to ensure none are lost */
  protected def backupTable(path: String, enabled: Boolean = true) = {
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
          name    := wf + s".backupTable(${FileUtil.getName(path)})",
          imports += ("java.io.{File, FileWriter}", "scala.io.Source.fromFile", "scala.collection.breakOut"),
          imports += "com.andreasschuh.repeat.core.Prefix.DONE",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
      else
        EmptyTask() set (
          name    := wf + s".keepTable(${FileUtil.getName(path)})",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
    Capsule(task, strainer = true)
  }

  /** Read previous result from backup table to save re-computation if nothing changed */
  protected def readFromTable(path: String, columns: Seq[_], values: Prototype[Array[Double]], enabled: Boolean = true) =
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
      ) set (
        name    := wf + s".read(${values.name})",
        imports += ("java.io.File","scala.io.Source.fromFile", "Double.NaN", "com.andreasschuh.repeat.core.Prefix.HAVE"),
        inputs  += (regId, parId, tgtId, srcId),
        outputs += (regId, parId, tgtId, srcId, values)
      ),
      strainer = true
    )

  /** Calculate mean of values over all registration results computed with a fixed set of parameters */
  protected def getMean(result: Prototype[Array[Double]], mean: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(
        s"""
          | val ncols = ${result.name}.length
          | val valid = ncols > 0 && !${result.name}.exists(_.isEmpty)
          | val ${mean.name} = if (valid) ${result.name}.transpose.map(_.sum / ncols) else Array[Double]()
        """.stripMargin
      ) set (
        name    := wf + s".getMean(${result.name})",
        inputs  += result.toArray,
        outputs += mean
      ),
      strainer = true
    )

  /** Calculate standard deviation of values over all registration results computed with a fixed set of parameters */
  protected def getSD(result: Prototype[Array[Double]], sigma: Prototype[Array[Double]]) =
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
        name    := wf + s".getSD(${result.name})",
        imports += "scala.math.{pow, sqrt}",
        inputs  += result.toArray,
        outputs += sigma
      ),
      strainer = true
    )

  /** Calculate mean and standard deviation of values over all registration results computed with a fixed set of parameters */
  protected def getMeanAndSD(result: Prototype[Array[Double]], mean: Prototype[Array[Double]], sigma: Prototype[Array[Double]]) =
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
        name    := wf + s".getMeanAndSD(${result.name})",
        imports += "scala.math.{pow, sqrt}",
        inputs  += result.toArray,
        outputs += (mean, sigma)
      ),
      strainer = true
    )

  /** Write individual registration result to CSV table */
  protected def saveToTable(path: String, header: Seq[_], result: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(s"""println(SAVE + s"${result.name.capitalize} for $regParTgtAndSrcInfo") """) set (
        name    := wf + s".saveToTable(${result.name})",
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
  protected def saveToSummary(path: String, header: Seq[_], mean: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(s"""println(SAVE + s"${mean.name.capitalize} for $regAndParInfo") """) set (
        name    := wf + s".saveToSummary(${mean.name})",
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
  protected def finalizeTable(path: String, enabled: Boolean = true) = {
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
          name    := wf + s".finalizeTable(${FileUtil.getName(path)}})",
          imports += ("scala.io.Source.fromFile", "scala.collection.breakOut","com.andreasschuh.repeat.core.Prefix.DONE"),
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
      else
        EmptyTask() set (
          name    := wf + s".keepTable(${FileUtil.getName(path)}})",
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
