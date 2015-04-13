package com.andreasschuh.repeat

import java.io.File
import scala.collection.JavaConverters._

/**
 * Registration evaluation measure
 */
object Measure extends Configurable("measure") {

  /// Names of label sets (ROIs) in expert annotations
  val segments = getPropertyKeySet(".segmentation.labels")

  /// Regions of interest (ROIs) over which to compute evaluation measures
  val regions = getStringListProperty("regions").toSet

  /// Set of segment labels whose union defines a given region of interest (ROI)
  def labels(region: String): Set[Int] = {
    getStringListProperty(s".segmentation.labels.$region").flatMap(
      item => if (item.contains(" to ")) {
        val range = item.split(" to ")
        val start = range(0).toInt
        val end   = range(1).toInt
        (start to end).toList
      } else if (segments.contains(item)) {
        labels(item).toList
      } else List(item.toInt)
    ).toSet
  }

  /// Compute matching matrix for each ROI
  def labelStats(a: File, b: File) = IRTK.labelStats(a, b, regions.map(region => region -> labels(region)).toMap)

  /// Compute Jaccard index for each ROI
  def jaccard(a: File, b: File) = labelStats(a, b).map{
    case (region, n: (Int, Int, Int, Double, Double)) => region -> n._4
  }

  /// Compute Dice coefficient for each ROI
  def dice(a: File, b: File) = labelStats(a, b).map{
    case (region, n: (Int, Int, Int, Double, Double)) => region -> n._5
  }
}
