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

import java.nio.file.{Paths, Files}


/**
 * Information about input datasets used for evaluation
 */
object Dataset extends Configurable("dataset") {

  /** Default base directory for all datasets */
  val topDir = getPathOptionProperty("dir") getOrElse Paths.get(System.getProperty("user.home"))

  /** Whether the files in the datasets directory are read-/writable by cluster compute nodes */
  val shared = getBooleanOptionProperty("shared") getOrElse false

  /** Names of available/configured datasets */
  val names = getPropertyKeySet(".dataset") - ("dir", "shared", "template", "use")

  /** Names of datasets to use for evaluation */
  val use = {
    val datasets = getStringListOptionProperty("use").getOrElse(names.toList).distinct
    val unknown  = datasets.filter(!names.contains(_))
    if (unknown.nonEmpty) throw new Exception("Cannot use unknown datasets: " + unknown.mkString(", "))
    datasets
  }

  /** ID of default template image used as reference for evaluation if no dataset specific templates are available */
  val refId = getStringProperty("template.id")

  /** Path of default template image */
  val refDir = topDir.resolve(getPathProperty("template.dir")).normalize

  /** File name template of default template image */
  val refName = getStringProperty("template.name")

  /** Background value in default template image */
  val padVal = getStringProperty("template.bgvalue") match {
    case "nan" => Double.NaN
    case s => s.toDouble
  }

  /** Get information object for named dataset */
  def apply(name: String) = new Dataset(name)
}

/**
 * Information about input dataset used for evaluation
 */
class Dataset(val id: String) extends Configurable("dataset." + id) {

  /** Default base directory of this dataset */
  val dir = getPathOptionProperty("dir") getOrElse {
    val subDir = Dataset.topDir.resolve(id)
    if (Files.isDirectory(subDir)) subDir
    else if (Files.isDirectory(Dataset.topDir)) Dataset.topDir
    else throw new Exception("Directory of " + id + " dataset does not exist: " + subDir)
  }

  /** Whether the files in this dataset are read-/writable by cluster compute nodes */
  val shared = getBooleanOptionProperty("shared") getOrElse Dataset.shared

  /** CSV file listing image IDs */
  val imgCsv = dir.resolve(getStringProperty("images.csv")).normalize

  /** Local directory containing the intensity images */
  val imgDir = dir.resolve(getStringProperty("images.dir")).normalize

  /** File name template of input intensity image */
  val imgName = getStringProperty("images.name")

  /** Get dataset intensity image file path given the image ID */
  def imgPath(imgId: String) = imgDir.resolve(imgName.replace("${imgId}", imgId))

  /** CSV file listing segmentation label numbers and names */
  val segCsv = dir.resolve(getStringProperty("labels.csv")).normalize

  /** Local directory containing ground truth segmentation images */
  val segDir = dir.resolve(getStringProperty("labels.dir")).normalize

  /** File name template of input segmentation image */
  val segName = getStringProperty("labels.name")

  /** Get dataset segmentation image file path given the image ID */
  def segPath(imgId: String) = segDir.resolve(segName.replace("${imgId}", imgId))

  /** Local directory containing foreground masks */
  val mskDir = dir.resolve(getStringProperty("masks.dir")).normalize

  /** File name template of input mask image */
  val mskName = getStringProperty("masks.name")

  /** Get dataset image mask file path given the image ID */
  def mskPath(imgId: String) = {
    val msk = mskDir.resolve(mskName.replace("${imgId}", imgId))
    if (Files.exists(msk)) Some(msk) else None
  }

  /** ID of dataset specific template image */
  val refId = getStringOptionProperty("template.id") getOrElse Dataset.refId

  /** Template image used for spatial normalization */
  val refDir = getStringOptionProperty("template.dir") match {
    case Some(s) => dir.resolve(s).normalize
    case None => Dataset.refDir
  }

  /** File name template of input reference/template image */
  val refName = getStringOptionProperty("template.name") getOrElse Dataset.refName

  /** Get dataset template image file path given the template ID */
  def refPath(refId: String) = refDir.resolve(refName.replace("${refId}", refId))

  /** Background intensity, padding value */
  val padVal = getStringOptionProperty("template.bgvalue") match {
    case Some("nan") => Double.NaN
    case Some(s) => s.toDouble
    case None => Dataset.padVal
  }
}
