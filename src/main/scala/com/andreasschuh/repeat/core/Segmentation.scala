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
 * Contains information about segmentation labels and groups
 */
class Segmentation(dataset: DataSet) {

  protected val info  = CSVUtil.fromFile(dataset.segCsv.toFile)
  protected val nrows = info("Label Number").length

  /** All label numbers */
  val labels = info("Label Number").map(_.toInt)

  /** Names of all labels */
  val names = {
    val name = info("Label Name")
    for (i <- 0 until nrows) yield labels(i) -> name(i)
  }.toMap

  /** Prefix of label group header */
  val groupPrefix = "Label Group: "

  /** Names of label groups */
  lazy val groupNames = info.keySet.filter(_.startsWith(groupPrefix)).map(_.drop(groupPrefix.length))

  /** Set of label numbers comprising each label group */
  lazy val groupLabels = groupNames.map {
    groupName => groupName -> {
      val selected = info(groupPrefix + groupName).map(_.toLowerCase)
      for (i <- 0 until nrows) yield if (selected(i) == "+" || selected(i) == "yes") Some(labels(i)) else None
    }.flatten.toSet
  }.toMap

  /** Set of all label numbers */
  def labelSet = labels.toSet

}
