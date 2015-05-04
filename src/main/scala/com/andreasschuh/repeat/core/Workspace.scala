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

import FileUtil.{join, normalize}


/**
 * Workspace settings
 */
object Workspace extends Configurable("workspace") {

  /** Whether workspace directory is readable/writeable by all remote compute nodes */
  val shared = getBooleanProperty("shared")

  /** Top-level directory of workspace */
  val dir = getFileProperty("dir")

  /** Working directory for OpenMOLE environments */
  val openMOLE = Some(join(dir, "tmp").getAbsolutePath)

  /** Whether dataset files have to be copied to workspace */
  def copyDataset = shared && !Dataset.shared && Bin.shared

  /** Local directory containing input images of dataset */
  val imgDir = if (copyDataset) {
    normalize(join(dir, getStringProperty("images.dir")))
  } else {
    Dataset.imgDir
  }

  /** Local directory containing input label images of dataset */
  val segDir = if (copyDataset) {
    normalize(join(dir, getStringProperty("labels.dir")))
  } else {
    Dataset.segDir
  }

  /** Local directory containing input template image */
  val refDir = if (copyDataset) imgDir else Dataset.refIm.getParentFile

  /** Local path of input template image */
  val refIm  = join(refDir, Dataset.refIm.getName)

  val dofDir = normalize(join(dir,    getStringProperty("dofs.dir")))
  val dofRig = normalize(join(dofDir, getStringProperty("dofs.rigid")))
  val dofIni = normalize(join(dofDir, getStringProperty("dofs.initial")))
  val dofAff = normalize(join(dofDir, getStringProperty("dofs.affine")))
  val dofPre = getStringProperty("dofs.prefix")
  val dofSuf = getStringProperty("dofs.suffix")
  val logDir = normalize(join(dir, getStringProperty("logs.dir")))
  val logSuf = getStringProperty("logs.suffix")
}
