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
import scala.language.reflectiveCalls

import com.andreasschuh.repeat.core.{Environment => Env, FileUtil}

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.plugin.task.scala._


/**
 * Copy files from the local dataset to the shared workspace
 */
object CopyFilesTo {

  /**
   * Puzzle piece to copy files to directory
   *
   * Note that the files are only being copied if newer than any existing file in the destination.
   *
   * @param dstDir             Destination directory
   * @param inputFiles[in,out] Files in workflow which should be copied
   *
   * @return Puzzle piece to copy files to target directory
   */
  def apply(dstDir: File, inputFiles: Prototype[File]*) = {
    val dir = dstDir.getAbsolutePath
    val valNames = inputFiles.toSeq.map(_.name)
    val defineTargets = "object target {\n  " + valNames.map { name =>
      s"""  val $name = new java.io.File("$dir", input.$name.getName)"""
    }.mkString("\n") + "\n}"
    val copyFiles  = valNames.map(name => s"copy(input.$name, target.$name)").mkString("\n")
    val setOutputs = valNames.map(name => s"val $name = target.$name").mkString("\n")
    val task = ScalaTask(defineTargets + "\n" + copyFiles + "\n" + setOutputs) set (
        name        := "CopyFiles",
        imports     += "com.andreasschuh.repeat.core.FileUtil.copy",
        usedClasses += FileUtil.getClass
      )
    inputFiles.foreach(p => {
      task.addInput (p)
      task.addOutput(p)
    })
    Capsule(task, strainer = true) on Env.local
  }
}
