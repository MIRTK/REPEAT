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

import java.io.File


/**
 * Workspace settings
 */
object Workspace extends Configurable("workspace") {

  /** Whether workspace directory is readable/writeable by all remote compute nodes */
  val shared = getBooleanProperty("shared") || Environment.localOnly

  /** Top-level directory of workspace */
  val dir = getFileProperty("dir")

  /**
   * Directory containing input/output data files
   *
   * This directory is used as root of file system used by PRoot if workspace is shared.
   */
  val rootFS = new File(dir, "rootfs")

  /** Whether dataset files have to be copied to workspace */
  def copyDataset = shared && !Dataset.shared && Bin.shared

  /** Local directory containing input images of dataset */
  val imgDir = if (copyDataset) new File(rootFS, getStringProperty("images.dir")) else Dataset.imgDir

  /** Local directory containing input label images of dataset */
  val segDir = if (copyDataset) new File(rootFS, getStringProperty("labels.dir")) else Dataset.segDir

  /** Local directory containing input template image */
  val refDir = if (copyDataset) imgDir else Dataset.refIm.getParentFile

  /** Local path of input template image */
  val refIm  = new File(refDir, Dataset.refIm.getName)

  val dofDir = new File(rootFS, getStringProperty("dofs.dir"))
  val dofRig = new File(rootFS, getStringProperty("dofs.rigid"))
  val dofIni = new File(rootFS, getStringProperty("dofs.initial"))
  val dofAff = new File(rootFS, getStringProperty("dofs.affine"))
  val dofPre = getStringProperty("dofs.prefix")
  val dofSuf = getStringProperty("dofs.suffix")
  val outDir = new File(rootFS, getStringProperty("output.dir"))
  val resDir = new File(rootFS, getStringProperty("results.dir"))
  val logDir = new File(rootFS, getStringProperty("logs.dir"))
  val logSuf = getStringProperty("logs.suffix")
}
