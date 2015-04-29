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
 * Information about input dataset used for evaluation
 */
object Dataset extends Configurable("dataset") {

  /** Whether the files in the dataset are readable by remote compute nodes */
  val shared = getBooleanProperty("shared") || Environment.localOnly

  /** Top-level directory of dataset */
  val dir = getFileProperty("dir")

  /** CSV file listing subject IDs of images */
  val imgCsv = normalize(join(dir, getStringProperty("images.csv")))

  /** Directory containing the images */
  val imgDir = normalize(join(dir, getStringProperty("images.dir")))

  /** Image file name prefix (before subject ID) */
  val imgPre = getStringProperty("images.prefix")

  /** Image file name suffix (after subject ID) */
  val imgSuf = getStringProperty("images.suffix")

  /** CSV file listing segmentation label numbers and names */
  val segCsv = normalize(join(dir, getStringProperty("labels.csv")))

  /** Directory containing ground truth segmentation images */
  val segDir = normalize(join(dir, getStringProperty("labels.dir")))

  /** Segmentation image file name prefix (before subject ID) */
  val segPre = getStringProperty("labels.prefix")

  /** Segmentation image file name suffix (after subject ID) */
  val segSuf = getStringProperty("labels.suffix")

  /** ID of template image */
  val refId  = getStringProperty("template.id")

  /** Template image used for spatial normalization */
  val refIm  = normalize(join(dir, getStringProperty("template.image")))

  /** Directory of template image */
  val refDir = refIm.getParentFile

  /** Name of template image (incl. extension) */
  val refName = refIm.getName

  /** File name extension of template image */
  val refSuf = FileUtil.getExtension(refIm)

  /** Background intensity */
  val bgVal = getIntProperty("images.bgvalue")
}