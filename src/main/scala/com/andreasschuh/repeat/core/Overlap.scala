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

//import java.io.File
//import scala.language.implicitConversions
//
//
///**
// * Registration evaluation measure
// */
//object Overlap extends Configurable("evaluation.overlap") {
//
//  /**
//   * Type of overlap similarity measure "enumeration" values/indices
//   * @param i Index of similarity measure in overlap measurement vector
//   */
//  sealed abstract class Measure(i: Int) { def toInt: Int = i }
//
//  /** Invalid overlap measure */
//  case object Invalid extends Measure(-1)
//
//  /** Jaccard similarity index */
//  case object JSI extends Measure(0)
//
//  /** Dice similarity coefficient */
//  case object DSC extends Measure(1)
//
//  /**
//   * Implicit conversion of Overlap.Measure case object to Int
//   * @param m Overlap measure enumeration case object
//   * @return IRTK.labelStats array index
//   */
//  implicit def measureToInt(m: Measure): Int = m.toInt
//
//  /** Convert string to overlap measure case object */
//  def measureFromString(s: String): Measure = s match {
//    case "DSC" | "Dice" => DSC
//    case "JSI" | "Jaccard" => JSI
//    case _ => Invalid
//  }
//
//  /**
//   * Names of label groups for which the overlap measures are computed
//   */
//  val groups = getStringListProperty("groups")
//
//  /**
//   * Union of all label numbers comprising the label groups for which the overlap is computed
//   * in the order specified by Label.number (i.e., the "Label Number" column of the input CSV file)
//   */
//  lazy val labels: List[Int] = {
//    val union = Segmentation.groupNames.filter(groups.contains(_)).foldLeft(Set[Int]()) {
//      (b, a) => b | Segmentation.groupLabels(a)
//    }
//    Segmentation.labels.map(label => if (union.contains(label)) Some(label) else None).flatten.toList
//  }
//
//  /** Set of overlap measures to calculate */
//  val measures = getStringListProperty("measure").map(s => measureFromString(s)).toSet
//
//  /**
//   * Compute overlap measures for each label
//   * @param a Segmentation a.
//   * @param b Segmentation b.
//   * @return A map from label number to a vector of different overlap measures computed from the same voxel counts
//   *         to avoid having to iterate over segmentation images multiple times if more than one measure is needed.
//   */
//  def apply(a: File, b: File, which: Measure = DSC) = new Overlap(a, b, which)
//
//  /**
//   * Create new overlap measure object
//   * @param stats Label statistics compute by IRTK.labelStats
//   * @param which Which overlap measure to extract
//   */
//  def apply(stats: Map[Int, Array[Double]], which: Measure) = new Overlap(stats, which)
//}
//
//
///**
// * Registration overlap measures
// * @param stats Label statistics compute by IRTK.labelStats
// * @param which Which overlap measure to extract
// */
//class Overlap(stats: Map[Int, Array[Double]], which: Overlap.Measure = Overlap.DSC) {
//
//  /**
//   * Create object of overlap measures comparing two segmentation images
//   * @param a Segmentation a
//   * @param b Segmentation b
//   * @param which Which overlap measure to extract
//   */
//  def this(a: File, b: File, which: Overlap.Measure) = {
//    this(IRTK.labelStats(a, b, Some(Overlap.labels.toSet)))
//  }
//
//  /**
//   * Get label overlap measure value
//   * @param label Label number
//   * @return Overlap measure value of specified label
//   */
//  def apply(label: Int): Double = stats(label)(which)
//
//  /**
//   * Get map from label to specified overlap measure
//   * @return Map from label number to overlap measure
//   */
//  def toMap: Map[Int, Double] = stats.map { case (label, measure) => label -> measure(which) }
//
//  /**
//   * Get array containing specified overlap measure values
//   * @return Array of overlap measures in the order specified by Label.number
//   */
//  def toArray: Array[Double] = Overlap.labels.map(label => stats(label)(which)).toArray
//
//  /**
//   * Mean of overlap measure for each label group
//   * @return Map from label group name to mean of overlap measure
//   */
//  lazy val mean: Map[String, Double] = Overlap.groups.map {
//    group => {
//      var mean   = .0
//      val labels = if (group.toLowerCase == "all" || group.toLowerCase == "overall") Overlap.labels else Segmentation.groupLabels(group)
//      labels.foreach( label => mean += stats(label)(which) )
//      if (labels.size > 0) mean /= labels.size
//      group -> mean
//    }
//  }.toMap
//
//  /**
//   * Standard deviation of overlap measure for each label group
//   * @return Map from label group name to standard deviation of overlap measure
//   */
//  lazy val sigma: Map[String, Double] = Overlap.groups.map {
//    group => {
//      var mean2  = .0
//      val labels = if (group.toLowerCase == "all" || group.toLowerCase == "overall") Overlap.labels else Segmentation.groupLabels(group)
//      labels.foreach( label => mean2 += math.pow(stats(label)(which), 2))
//      if (labels.size > 0) mean2 /= labels.size
//      group -> math.sqrt(mean2 - math.pow(mean(group), 2))
//    }
//  }.toMap
//
//  /**
//   * Compute mean of overlap measure for each label group
//   * @return Array of mean overlap measure for each label group in the order of Overlap.groups
//   */
//  def getMeanValues: Array[Double] = {
//    Overlap.groups.map(group => mean(group)).toArray
//  }
//
//  /**
//   * Compute standard deviation of overlap measure for each label group
//   * @return Array of standard deviation of overlap measure for each label group in the order of Overlap.groups
//   */
//  def getSigmaValues: Array[Double] = {
//    Overlap.groups.map(group => sigma(group)).toArray
//  }
//}
