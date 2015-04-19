package com.andreasschuh.repeat.puzzle

import java.io.File
import scala.language.reflectiveCalls

import com.andreasschuh.repeat.core.{Environment => Env, FileUtil}

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.plugin.task.scala._


/**
 * Workflow puzzle to copy files to the shared workspace
 */
object CopyFilesTo {
  def apply(dstDir: File, inputFiles: Prototype[File]*) = {
    val dir = dstDir.getAbsolutePath
    val valNames = inputFiles.toSeq.map(_.name)
    val defineTargets = "object target {\n  " + valNames.map { name =>
      s"""  val $name = new File(new File("$dir"), input.$name.getName)"""
    }.mkString("\n") + "\n}"
    val copyFiles  = valNames.map(name => s"copy(input.$name, target.$name)").mkString("\n")
    val setOutputs = valNames.map(name => s"val $name = target.$name").mkString("\n")
    val task = ScalaTask(defineTargets + "\n" + copyFiles + "\n" + setOutputs) set (
        name        := "CopyFiles",
        imports     += ("java.io.File", "com.andreasschuh.repeat.core.FileUtil.copy"),
        usedClasses += FileUtil.getClass
      )
    inputFiles.foreach(p => {
      task.addInput (p)
      task.addOutput(p)
    })
    Capsule(task, strainer = true) on Env.local
  }
}
