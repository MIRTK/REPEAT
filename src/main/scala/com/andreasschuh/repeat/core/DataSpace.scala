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


/**
 * Get new workspace info object for a specific dataset
 */
object DataSpace {
  def apply(setId: String) = new DataSpace(setId)
}


/**
 * Workspace for a specific dataset
 */
class DataSpace(val setId: String) extends WorkSpace {

  /** Background intensity, padding value of intensity images */
  val imgBkg = DataSet(setId).imgBkg

  /** Background intensity, padding value of template image */
  val refBkg = DataSet(setId).refBkg

  /** ID of template image */
  val refId = DataSet(setId).refId

  /** Template image used for spatial normalization */
  def refImg(refId: String) = WorkSpace.refImg(setId = setId, refId = refId)

  /** Get file path of log files directory */
  def logDir = WorkSpace.logDir(setId = setId)

  /** Get log file path */
  def logPath(s: String*) = s.flatMap(_.split("/")).foldLeft(logDir)( (b, a) => b.resolve(a) )

  /** Get file path of image meta-data table */
  def imgCsv = WorkSpace.imgCsv(setId = setId)

  /** Get file path of segmentation labels table */
  def segCsv = WorkSpace.segCsv(setId = setId)

  /** Get file path of registration parameters table */
  def parCsv(regId: String) = WorkSpace.parCsv(setId = setId, regId = regId)

  /** Get file path of original intensity image */
  def orgImg(imgId: String) = WorkSpace.orgImg(setId = setId, imgId = imgId)

  /** Get file path of original image mask */
  def orgMsk(imgId: String) = WorkSpace.orgMsk(setId = setId, imgId = imgId)

  /** Get file path of original segmentation image */
  def orgSeg(imgId: String) = WorkSpace.orgSeg(setId = setId, imgId = imgId)

  /** Get file path of padded intensity image */
  def padImg(imgId: String) = WorkSpace.padImg(setId = setId, imgId = imgId)

  /** Get file path of image voxel-center points file */
  def imgPts(imgId: String) = WorkSpace.imgPts(setId = setId, imgId = imgId)

  /** Get file path of affine target to image transformation */
  def affDof(regId: String, tgtId: String, srcId: String) = WorkSpace.affDof(setId = setId, regId = regId, tgtId = tgtId, srcId = srcId)

  /** Get file path of affine target to image transformation */
  def phiDof(regId: String, parId: String, tgtId: String, srcId: String, phiPre: String = "", phiSuf: String = Suffix.dof) =
    WorkSpace.phiDof(setId = setId, regId = regId, parId = parId, tgtId = tgtId, srcId = srcId, phiPre = phiPre, phiSuf = phiSuf)

  /** Get file path of output directory for CSV files listing evaluation results */
  def csvDir(regId: String, parId: String) =
    WorkSpace.csvDir(setId = setId, regId = regId, parId = parId)

}
