package com.andreasschuh.repeat.core

import scala.language.postfixOps

import java.io.File
import java.nio.file.Files
import scala.sys.process._
import scala.util.Try


/**
 * Package executables using CARE and re-execute them within a workflow task
 *
 * @see http://reproducible.io
 */
object Bin extends Configurable("software") {

  /** Whether software installations are shared by all compute nodes */
  val shared = getBooleanProperty("shared") || Environment.localOnly

  /** Local path of the "care" executable used to pack software tools */
  protected val care = getFileProperty("care")

  /** Local path of the "tar" executable used to create and extract archive files */
  protected val tar = getFileProperty("tar")

  /** Directory for temporary files during packing process */
  protected val tmp = getFileProperty("care-tmp")

  /** Directory containing pre-packed CARE archives */
  protected val repository = getFileProperty("repository")

  /**
   * Pack executable files
   * @param destDir Shared working directory
   * @param commands Names or paths of executable to pack including arguments for initial execution
   * @return Resource files needed by tasks to execute commands
   */
  def pack(destDir: File, commands: Cmd*): Seq[File] = {
    if (destDir.exists && !destDir.isDirectory) throw new Exception("Expected destination directory, not file")
    destDir.mkdirs()
    if (commands.size == 0) return Seq[File]()
    Seq(new File(destDir, "proot")) ++ commands.map { app =>
      val destBin = new File(destDir, app.head)
      if (!destBin.exists) {
        println("Packing " + app.head)
        val returnCode = (Cmd(care.getAbsolutePath, "-o", destDir.getAbsolutePath + "/") ++ app).!
        if (returnCode != 0) throw new Exception("Failed to pack " + app.head)
        Files.move(destDir.toPath.resolve("re-execute.sh"), destDir.toPath.resolve(app.head))
        println("Finished packing " + app.head)
      }
      destBin
    }
  }

  /**
   * Pack executable files
   *
   * @param archive Name of archive file (excluding extension)
   * @param commands Names or paths of executable to pack including arguments for initial execution
   * @return Archive file needed by tasks to execute packed commands
   */
  def pack(archive: String, commands: Cmd*): File = {
    val archiveFile = new File(repository, archive + ".tar.bz2")
    if (!archiveFile.exists) {
      val destDir = new File(tmp, archive)
      pack(destDir, commands: _*)
      val returnCode = Cmd(tar.getAbsolutePath, "-cjf", archiveFile.getAbsolutePath, destDir.getAbsolutePath).!
      if (returnCode != 0) throw new Exception("Failed to archive directory: " + destDir.getAbsolutePath)
      //Try(destDir.deleteRecursively(continueOnFailure = true))
    }
    archiveFile
  }

  /**
   * Unpack executable files
   *
   * @param archive   Name of archive file to unpack (excluding extension)
   * @param workspace Directory into which to unpack the archive content
   */
  def unpack(archive: String, workspace: File, overwrite: Boolean = true): Int = {
    val opts = if (overwrite) "-xj" else "-xjk"
    Cmd(tar.getAbsolutePath, opts, "-C", workspace.getAbsolutePath, "-f", new File(repository, s"$archive.tar.bz2").getAbsolutePath, "--strip", "1").!
  }

  /**
   * Execute command
   *
   * @param archive   Name of archive containing the executable
   * @param command   Command to execute and its arguments
   * @param workspace Workspace into which to unpack the archive files before execution.
   *                  If the directory exists, only non-existing files are written. Otherwise,
   *                  the existing files are used. After extraction, the workspace will contain the
   *                  shell script needed to re-execute the command and the rootfs directory.
   */
  def execute(archive: String, command: Cmd, workspace: File): Int = {
    unpack(archive, workspace, overwrite = false) // NOP if done before
    Cmd(new File(workspace, s"execute-${command.head}").getAbsolutePath) ++ command.tail !
  }
}

