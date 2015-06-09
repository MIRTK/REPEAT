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
 * Get new workspace info object for a specific dataset and registration
 */
object EvalSpace {
  def apply(setId: String, regId: String) = new EvalSpace(setId, regId)
}


/**
 * Workspace for a specific dataset and registration
 */
class EvalSpace(setId: String, val regId: String) extends DataSpace(setId) {

  /** Get file path of registration log files directory */
  def logDir(parId: String) = WorkSpace.logDir(setId = setId).resolve(regId + "-" + parId)

  /** Get file path of registration parameters table */
  def parCsv = WorkSpace.parCsv(setId = setId, regId = regId)

  /** Get file path of registered output image */
  def regImg(parId: String, imgId: String) = WorkSpace.regImg(setId = setId, regId = regId, parId = parId, imgId = imgId)

  /** Get file path of registered segmentation image */
  def regSeg(parId: String, imgId: String) = WorkSpace.regSeg(setId = setId, regId = regId, parId = parId, imgId = imgId)

  /** Get file path of intensity average image */
  def avgImg(parId: String, refId: String) = WorkSpace.avgImg(setId = setId, regId = regId, parId = parId, refId = refId)

}
