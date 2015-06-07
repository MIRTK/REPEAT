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

import java.nio.file.Path


/**
 * Workspace settings, i.e., location of input and output files (possibly with "${var}" place holders)
 */
object Workspace extends Configurable("workspace") {

  /** Whether workspace directory is read-/writable by cluster compute nodes */
  val shared = getBooleanOptionProperty("shared") getOrElse false

  /** Whether to append mean results to existing summary tables */
  val appCsv = getBooleanProperty("tables.append")

  /** Whether to append command output to existing log files */
  val appLog = getBooleanProperty("logs.append")

  /** Top-level directory of workspace */
  val dir = getPathProperty("dir")

  /** Shared directory used by OpenMOLE for cluster environments */
  val comDir: Option[String] = if (shared) Some(dir.resolve(FileUtil.hidden("openmole")).toString) else None

  //  /** File path template of local directory containing intensity images */
  //  val imgDir = dir.resolve(getStringProperty("images.dir")).normalize
  //
  //  /** Get file path of a particular intensity image directory */
  //  def imgDirPath(setId: String) = expand(imgDir, Map("setId" -> setId))
  //
  //  /** File path template of local directory containing segmentation images */
  //  val segDir = dir.resolve(getStringProperty("labels.dir")).normalize
  //
  //  /** Get file path of a particular segmentation image directory */
  //  def segDirPath(setId: String) = expand(segDir, Map("setId" -> setId))
  //
  //  /** File path template of local directory containing transformation files */
  //  val dofDir = dir.resolve(getStringProperty("dofs.dir")).normalize
  //
  //  /** Get file path of a particular transformations directory */
  //  def dofDirPath(setId: String) = expand(dofDir, Map("setId" -> setId))
  //
  //  /** Local directory containing result tables */
  //  val csvDir = dir.resolve(getStringProperty("tables.dir")).normalize

  /** File path template of directory containing log files */
  val logDir = dir.resolve(getStringProperty("logs.dir")).normalize

  /** Get file path of a particular log files directory */
  def logDir(setId: String): Path = expand(logDir, Map("setId" -> setId))

  /** File path template of image meta-data table */
  val imgCsv = dir.resolve(getStringProperty("images.csv")).normalize

  /** Get file path of a particular image meta-data table */
  def imgCsv(setId: String): Path = expand(imgCsv, Map("setId" -> setId))

  /** File path template of segmentation labels table */
  val segCsv = dir.resolve(getStringProperty("labels.csv")).normalize

  /** Get file path of a particular segmentation labels table */
  def segCsv(setId: String): Path = expand(segCsv, Map("setId" -> setId))

  /** File path template of registration parameters table */
  val parCsv = dir.resolve(getStringProperty("params")).normalize

  /** Get file path of a particular registration parameters table */
  def parCsv(setId: String, regId: String): Path =
    expand(parCsv, Map("setId" -> setId, "regId" -> regId))

  /** File path template of reference image */
  val refImg = dir.resolve(getStringProperty("images.template")).normalize

  /** Get file path of reference image */
  def refImg(setId: String, refId: String): Path =
    expand(refImg, Map("setId" -> setId, "refId" -> refId))

  /** File path template of original input image */
  val orgImg = dir.resolve(getStringProperty("images.orig")).normalize

  /** Get file path of particular original intensity image */
  def orgImg(setId: String, imgId: String): Path =
    expand(orgImg, Map("setId" -> setId, "imgId" -> imgId))

  /** File path template of original image mask */
  val orgMsk = dir.resolve(getStringProperty("images.mask")).normalize

  /** Get file path of a particular original image mask */
  def orgMsk(setId: String, imgId: String): Path =
    expand(orgMsk, Map("setId" -> setId, "imgId" -> imgId))

  /** File path template of padded/masked foreground image */
  val padImg = dir.resolve(getStringProperty("images.padded")).normalize

  /** Get file path of a particular padded intensity image */
  def padImg(setId: String, imgId: String): Path =
    expand(padImg, Map("setId" -> setId, "imgId" -> imgId))

  /** File path template of image voxel-center points */
  val imgPts = dir.resolve(getStringProperty("images.points")).normalize

  /** Get file path of a particular image voxel-center points file */
  def imgPts(setId: String, imgId: String): Path =
    expand(imgPts, Map("setId" -> setId, "imgId" -> imgId))

  /** File path template of registered output image */
  val regImg = dir.resolve(getStringProperty("images.output")).normalize

  /** Get file path of a particular registered output image */
  def regImg(setId: String, regId: String, parId: String, imgId: String): Path =
    expand(regImg, Map("setId" -> setId, "regId" -> regId, "parId" -> parId, "imgId" -> imgId))

  /** File path template of intensity average image */
  val avgImg = dir.resolve(getStringProperty("images.average")).normalize

  /** Get file path of a particular intensity average image */
  def avgImg(setId: String, regId: String, parId: String, refId: String): Path =
    expand(avgImg, Map("setId" -> setId, "regId" -> regId, "parId" -> parId, "refId" -> refId))

  /** File path template of original segmentation image */
  val orgSeg = dir.resolve(getStringProperty("labels.orig")).normalize

  /** Get file path of particular original segmentation image */
  def orgSeg(setId: String, imgId: String): Path =
    expand(orgSeg, Map("setId" -> setId, "imgId" -> imgId))

  /** File path template of registered output segmentation image */
  val regSeg = dir.resolve(getStringProperty("labels.output")).normalize

  /** Get file path of particular registered segmentation image */
  def regSeg(setId: String, regId: String, parId: String, imgId: String): Path =
    expand(regSeg, Map("setId" -> setId, "regId" -> regId, "parId" -> parId, "imgId" -> imgId))

  /** File path template of rigid template to image transformation */
  val rigDof = dir.resolve(getStringProperty("dofs.rigid")).normalize

  /** Get file path of particular rigid template to image transformation */
  def rigDof(setId: String, refId: String, imgId: String): Path =
    expand(rigDof, Map("setId" -> setId, "tgtId" -> refId, "srcId" -> imgId))

  /** File path template of affine template to image transformation */
  val affDof = dir.resolve(getStringProperty("dofs.affine")).normalize

  /** Get file path of particular affine template to image transformation */
  def affDof(setId: String, refId: String, imgId: String): Path =
    expand(affDof, Map("setId" -> setId, "tgtId" -> refId, "srcId" -> imgId))

}


class Workspace {

  /** Whether workspace directory is read-/writable by cluster compute nodes */
  val shared = Workspace.shared

  /** Whether to append mean results to existing summary tables */
  val appCsv = Workspace.appCsv

  /** Whether to append command output to existing log files */
  val appLog = Workspace.appLog

}