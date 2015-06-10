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

import org.openmole.core.dsl.Val
import org.openmole.core.workflow.builder._
import org.openmole.core.workflow.data._
import org.openmole.core.workflow.mole._
import org.openmole.core.workflow.puzzle._
import org.openmole.core.workflow.sampling._
import org.openmole.core.workflow.task._
import org.openmole.core.workflow.tools._
import org.openmole.core.workflow.transition._
import org.openmole.plugin.domain.collection._
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern._

import com.andreasschuh.repeat.core._
import com.andreasschuh.repeat.sampling._
import com.andreasschuh.repeat.source._



/**
 * Workflow puzzle
 *
 * @param start Capsule at end of parent workflow which has to finish first. This capsule must emit a Boolean "go"
 *              dataflow variable. Only when this variable is true the execution of this workflow begins.
 */
abstract class Workflow(start: Option[Capsule] = None) {

  // Name of workflow puzzle for identification in formal validation errors
  protected val wf = this.getClass.getSimpleName

  protected lazy val dataSet   = Val[DataSet]      ///< Dataset info object
  protected lazy val dataSpace = Val[DataSpace]    ///< Dataset specific workspace info object
  protected lazy val evalSpace = Val[EvalSpace]    ///< Dataset and registration specific workspace info object
  protected lazy val reg       = Val[Registration] ///< Registration info object

  // Commonly used workflow variable prototypes
  protected lazy val go     = Val[Boolean]
  protected lazy val setId  = Val[String]              ///< Dataset name/ID
  protected lazy val regId  = Val[String]              ///< Registration name/ID
  protected lazy val parCsv = Val[File]                ///< Registration parameters CSV file
  protected lazy val parVal = Val[Map[String, String]] ///< Registration parameters name/value map
  protected lazy val parIdx = Val[Int]                 ///< Row index of registration parameters
  protected lazy val parId  = Val[String]              ///< Parameter set ID
  protected lazy val refId  = Val[String]              ///< Reference image ID
  protected lazy val tgtId  = Val[String]              ///< Target image ID (i.e., fixed  image in pairwise registration)
  protected lazy val srcId  = Val[String]              ///< Source image ID (i.e., moving image in pairwise registration)
  protected lazy val imgId  = Val[String]              ///< An image ID

  // Info about workflow stream for inclusion in status messages
  protected lazy val setInfo = "{setId=${setId}}"
  protected lazy val regInfo = "{setId=${setId}, regId=${regId}}"
  protected lazy val regAndParInfo = "{setId=${setId}, regId=${regId}, parId=${parId}}"
  protected lazy val regParTgtAndSrcInfo = "{setId=${setId}, regId=${regId}, parId=${parId}, tgtId=${tgtId}, srcId=${srcId}}"

  /** OpenMOLE puzzle corresponding to this (sub-)workflow */
  def puzzle: Puzzle

  /** Empty strainer capsule */
  protected def nop(taskName: String) = Capsule(EmptyTask() set (name := wf + "." + taskName), strainer = true)

  /** First slot of this workflow puzzle */
  val first = Slot(nop("first"))

  /** Puzzle piece triggering start of workflow */
  lazy val begin = {
    def _start =
      Capsule(
        EmptyTask() set (
          name    := wf + ".start",
          outputs += go,
          go      := true
        )
      )
    (start getOrElse _start) -- (first when "go")
  }

  /** Slot at the end of entire workflow puzzle */
  val end =
    Slot(
      Capsule(
        EmptyTask() set (
          name    := wf + ".end",
          outputs += go,
          go      := true
        ),
        strainer = true
      )
    )

  /** Swap values of two prototype variables */
  protected def swap(p1: Prototype[_], p2: Prototype[_]) = {
    val _taskName = s"swap(${p1.name}, ${p2.name})"
    Strain(
      ScalaTask(
        """
          | val p1 = input.p2
          | val p2 = input.p1
        """.stripMargin
      ) set (
        name    := wf + "." + _taskName,
        inputs  += (p1, p2),
        outputs += (p1, p2)
      )
    ) -- getHead(Seq(p1, p2), taskName = Some(_taskName))
  }

  /** Explore datasets to be used, usually the start of any workflow puzzle */
  protected def forEachDataSet =
    Capsule(
      ExplorationTask(setId in DataSet.use) set (
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

  /** Inject dataset info object into workflow */
  protected def getDataSet =
    Capsule(
      EmptyTask() set (
        name    := wf + ".getDataSet",
        inputs  += (setId, dataSet),
        outputs += (setId, dataSet)
      ),
      strainer = true
    ) source DataSetSource(setId, dataSet)

  /** Inject dataset workspace info object into workflow */
  protected def getDataSpace =
    Capsule(
      EmptyTask() set (
        name    := wf + "getDataSpace",
        inputs  += (setId, dataSpace),
        outputs += (setId, dataSpace)
      ),
      strainer = true
    ) source DataSpaceSource(setId, dataSpace)

  /** Inject ID of dataset specific template image into workflow */
  protected def getRefId = {
    Capsule(
      ScalaTask("val refId = dataSpace.refId") set (
        name    := wf + ".getRefId",
        inputs  += dataSpace,
        outputs += refId
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
  protected def putRegId(id: String) =
    Capsule(
      EmptyTask() set (
        name    := wf + ".putRegId",
        outputs += regId,
        regId   := id
      ),
      strainer = true
    )

  /** Set regId to ID of input registration info object */
  protected def getRegId =
    Capsule(
      ScalaTask("val regId = reg.id") set (
        name    := wf + ".getRegId",
        inputs  += reg,
        outputs += regId
      ),
      strainer = true
    )

  /** Inject registration info object into workflow */
  protected def putReg(r: Registration) =
    Capsule(
      EmptyTask() set (
        name    := wf + ".putReg",
        outputs += (regId, reg),
        reg     := r,
        regId   := r.id
      ),
      strainer = true
    )

  /** Inject registration info object into workflow */
  protected def getReg =
    Capsule(
      EmptyTask() set (
        name    := wf + "getReg",
        inputs  += (regId, reg),
        outputs += (regId, reg)
      ),
      strainer = true
    ) source RegistrationSource(regId, reg)

  /** Inject registration parameters CSV file into workflow */
  protected def getParCsv =
    Capsule(
      EmptyTask() set (
        name    := wf + "getParCsv",
        inputs  += (regId, dataSpace, parCsv),
        outputs += (regId, dataSpace, parCsv)
      ),
      strainer = true
    ) source ParamsCSVFileSource(regId, dataSpace, parCsv)

  /** Explore parameter set IDs of a specific registration */
  protected def forEachParId = {
    // TODO: Unlike forEachPar, this requires an "ID" column to be present
    val sampling = CSVSampling("${dataSpace.parCsv(regId)}")
    sampling.addColumn("ID", parId)
    Capsule(
      ExplorationTask(sampling) set (
        name   := wf + ".forEachParId",
        inputs += (dataSpace, regId)
      ),
      strainer = true
    )
  }

  /** Explore parameter sets of a specific registration */
  protected def forEachPar =
    Capsule(
      ExplorationTask(CSVToMapSampling("${dataSpace.parCsv(regId)}", parVal) zipWithIndex parIdx) set (
        name   := wf + ".forEachPar",
        inputs += (dataSpace, regId)
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
          | val parVal = input.parVal + ("ID" -> parId)
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
      ExplorationTask(sampling) set (
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

  /** Explore each pair of images */
  protected def forEachImgPair = {
    val tgtIdSampling = CSVSampling("${dataSpace.imgCsv}")
    val srcIdSampling = CSVSampling("${dataSpace.imgCsv}")
    tgtIdSampling.addColumn("Image ID", tgtId)
    srcIdSampling.addColumn("Image ID", srcId)
    Capsule(
      ExplorationTask((tgtIdSampling x srcIdSampling) filter "tgtId != srcId") set (
        name   := wf + ".forEachUniqueImgPair",
        inputs += dataSpace
      ),
      strainer = true
    )
  }

  /** Explore each unique pair of images */
  protected def forEachUniqueImgPair = {
    val tgtIdSampling = CSVSampling("${dataSpace.imgCsv}")
    val srcIdSampling = CSVSampling("${dataSpace.imgCsv}")
    tgtIdSampling.addColumn("Image ID", tgtId)
    srcIdSampling.addColumn("Image ID", srcId)
    Capsule(
      ExplorationTask((tgtIdSampling x srcIdSampling) filter "tgtId < srcId") set (
        name   := wf + ".forEachUniqueImgPair",
        inputs += dataSpace
      ),
      strainer = true
    )
  }

  /** Copy file */
  protected def copy(from: Prototype[Path], to: Prototype[Path]) =
    Strain(
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
      )
    )

  /** Demux aggregated results by taking head element only */
  protected def getHead(input: Iterable[Prototype[_]], taskName: Option[String] = None) = {
    val inputNames = input.toSeq.map(_.name)
    val nameSuffix = s".getHead(${inputNames.mkString(",")})"
    val _taskName  = taskName match {
      case Some(s) => "." + s + nameSuffix
      case None => nameSuffix
    }
    val task =
      ScalaTask(inputNames.map(name => s"val $name = input.$name.head").mkString("\n")) set (
        name := wf + _taskName
      )
    input.foreach(p => {
      task.addInput(p.toArray)
      task.addOutput(p)
    })
    Capsule(task, strainer = true)
  }

  /** Demux aggregated results by taking head element only */
  protected def getHead(input: Prototype[_]*): Capsule = getHead(input.toSeq)

  /** Demux aggregated results by taking head element only */
  protected def getHead(taskName: String, input: Prototype[_]*): Capsule = getHead(input.toSeq, Some(taskName))

  /** Inject (local) file path into workflow */
  protected def putPath(path: ExpandedString, p: Prototype[Path], taskName: Option[String] = None) = {
    val _taskName = taskName getOrElse p.name
    Capsule(
      EmptyTask() set (
        name    := wf + s".putPath(${_taskName})",
        outputs += p
      )
    ) source PathSource(path, p)
  }

  /** Delete table with summary results which can be recomputed from individual result tables */
  protected def deleteTable(path: ExpandedString, enabled: Boolean = true, taskName: Option[String] = None) = {
    val _taskName = taskName getOrElse path.string.split(File.separator).last
    if (enabled) {
      val table = Prototype[Path]("table")
      Strain(
        ScalaTask(
          """
            | if (Files.exists(table)) {
            |   Files.delete(table)
            |   println(DONE + "Delete " + table)
            | }
          """.stripMargin
        ) set (
          name    := wf + s".deleteTable(${_taskName})",
          imports += ("java.nio.file.Files", "com.andreasschuh.repeat.core.Prefix.DONE"),
          inputs  += table
        ) source PathSource(path, table)
      )
    } else
      nop(s"keepTable(${_taskName})").toPuzzle
  }

  /** Make copy of previous result tables and merge them with previously copied results to ensure none are lost */
  protected def backupTable(path: ExpandedString, enabled: Boolean = true, taskName: Option[String] = None) = {
    val _taskName = taskName getOrElse path.string.split(File.separator).last
    val table = Prototype[Path]("table")
    if (enabled)
      Strain(
        ScalaTask(
          s"""
            | if (Files.exists(table)) {
            |   val backup = FileUtil.hidden(table)
            |   val l1 = if (Files.exists(backup)) Source.fromFile(backup).getLines().toList.drop(1) else List[String]()
            |   val l2 = Source.fromFile(table).getLines().toList
            |   val fw = new FileWriter(backup)
            |   try {
            |     fw.write(l2.head + "\\n")
            |     val l: List[String] = (l1 ::: l2.tail).groupBy( _.split(",").take(2).mkString(",") ).map(_._2.last)(breakOut)
            |     l.sortBy( _.split(",").take(2).mkString(",") ).foreach( row => fw.write(row + "\\n") )
            |   }
            |   finally fw.close()
            |   Files.delete(table)
            |   println(Prefix.DONE + s"Backup $${table.getFileName} for $regAndParInfo")
            | }
          """.stripMargin
        ) set (
          name        := wf + s".backupTable(${_taskName})",
          imports     += ("java.io.FileWriter", "java.nio.file.Files", "scala.io.Source", "scala.collection.breakOut"),
          imports     += "com.andreasschuh.repeat.core._",
          usedClasses += FileUtil.getClass,
          inputs      += (setId, regId, parId, table)
        ) source PathSource(path, table)
      )
    else
      nop(s"keepTable(${_taskName})").toPuzzle
  }

  /** Read previous result from backup table to save re-computation if nothing changed */
  protected def readFromTable(path: ExpandedString, columns: Seq[_], values: Prototype[Array[Double]],
                              enabled: Boolean = true, taskName: Option[String] = None) = {
    val _taskName = taskName getOrElse values.name
    val table = Prototype[Path]("table")
    Strain(
      ScalaTask(
        s"""
          | val enabled = $enabled
          | val columns = Array[String](${if (columns.isEmpty) "" else "\"" + columns.mkString("\", \"") + "\""})
          |
          | val ${values.name} =
          |   if (enabled)
          |     try {
          |       val file = FileUtil.hidden(table).toFile
          |       val rows = Source.fromFile(file).getLines().toList
          |       if (!rows.head.startsWith("Target,Source,")) throw new Exception("Invalid table " + file.getPath)
          |       val hdr = rows.head.split(",").zipWithIndex.toMap
          |       val row = rows.tail.view.filter(_.startsWith(s"$${tgtId},$${srcId},")).last.split(",")
          |       if (row.size != hdr.size) throw new Exception("Invalid table " + file.getPath)
          |       val values = columns.map(name => row(hdr(name)).toDouble)
          |       println(Prefix.HAVE + s"${_taskName.capitalize} for $regParTgtAndSrcInfo")
          |       values
          |     }
          |     catch {
          |       case _: Exception => Array[Double]()
          |     }
          |   else Array[Double]()
        """.stripMargin
      ) set (
        name        := wf + s".read(${_taskName})",
        imports     += ("java.io.File","scala.io.Source", "Double.NaN", "com.andreasschuh.repeat.core._"),
        usedClasses += FileUtil.getClass,
        inputs      += (setId, regId, parId, tgtId, srcId, table),
        outputs     += values
      ) source PathSource(path, table)
    )
  }

  /** Calculate mean of values over all registration results computed with a fixed set of parameters */
  protected def getMean(result: Prototype[Array[Double]], mean: Prototype[Array[Double]]) =
    Strain(
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
      )
    )

  /** Calculate standard deviation of values over all registration results computed with a fixed set of parameters */
  protected def getSD(result: Prototype[Array[Double]], sigma: Prototype[Array[Double]]) =
    Strain(
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
      )
    )

  /** Calculate mean and standard deviation of values over all registration results computed with a fixed set of parameters */
  protected def getMeanAndSD(result: Prototype[Array[Double]], mean: Prototype[Array[Double]], sigma: Prototype[Array[Double]]) =
    Strain(
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
      )
    )

  /** Write individual registration result to CSV table */
  protected def saveToTable(table: ExpandedString, header: Seq[_], result: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(s"""println(SAVE + s"${result.name.capitalize} for $regParTgtAndSrcInfo")""") set (
        name    := wf + s".saveToTable(${result.name})",
        imports += "com.andreasschuh.repeat.core.Prefix.SAVE",
        inputs  += (setId, regId, parId, tgtId, srcId, result),
        outputs += (setId, regId, parId, tgtId, srcId, result)
      )
    ) hook (
      AppendToCSVFileHook(table, tgtId, srcId, result) set (
        csvHeader := "Target,Source," + header.mkString(","),
        singleRow := true
      )
    )

  /** Write mean values calculated over all registration results computed with a fixed set of parameters to CSV table */
  protected def saveToSummary(table: ExpandedString, header: Seq[_], mean: Prototype[Array[Double]]) =
    Capsule(
      ScalaTask(s"""println(SAVE + s"${mean.name.capitalize} for $regAndParInfo")""") set (
        name    := wf + s".saveToSummary(${mean.name})",
        imports += "com.andreasschuh.repeat.core.Prefix.SAVE",
        inputs  += (setId, regId, parId, mean),
        outputs += (setId, regId, parId, mean)
      )
    ) hook (
      AppendToCSVFileHook(table, regId, parId, mean) set (
        csvHeader := "Registration,Parameters," + header.mkString(","),
        singleRow := true
      )
    )

  /** Finalize result table, appending non-overwritten previous results again and sorting the final table */
  protected def finalizeTable(path: ExpandedString, enabled: Boolean = true, taskName: Option[String] = None) = {
    val _taskName = taskName getOrElse path.string.split(File.separator).last
    if (enabled) {
      val table = Prototype[Path]("table")
      Strain(
        ScalaTask(
          s"""
            | val backup = FileUtil.hidden(table)
            | if (Files.exists(backup)) {
            |   val l1 = Source.fromFile(backup).getLines().toList
            |   val l2 = if (Files.exists(table)) Source.fromFile(table).getLines().toList.tail else List[String]()
            |   val fw = new FileWriter(table)
            |   try {
            |     fw.write(l1.head + "\\n")
            |     val l: List[String] = (l1.tail ::: l2).groupBy( _.split(",").take(2).mkString(",") ).map(_._2.last)(breakOut)
            |     l.sortBy( _.split(",").take(2).mkString(",") ).foreach( row => fw.write(row + "\\n") )
            |   }
            |   finally fw.close()
            |   Files.delete(backup)
            |   println(Prefix.DONE + s"Finalize $${table.getFileName} for $regAndParInfo")
            | }
          """.stripMargin
        ) set (
          name        := wf + s".finalizeTable(${_taskName})",
          imports     += ("java.io.File", "java.nio.file.Files", "scala.io.Source", "scala.collection.breakOut"),
          imports     += "com.andreasschuh.repeat.core._",
          usedClasses += FileUtil.getClass,
          inputs      += (setId, regId, parId, table)
        ) source PathSource(path, table)
      )
    }
    else
      nop(s"keepTable(${_taskName}})").toPuzzle
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
