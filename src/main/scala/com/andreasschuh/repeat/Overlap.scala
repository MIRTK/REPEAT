package com.andreasschuh.repeat

import java.io.File
import scala.language.implicitConversions


/**
 * Registration evaluation measure
 */
object Overlap extends Configurable("overlap") {

  /**
   * Type of overlap similarity measure "enumeration" values/indices
   * @param i Index of similarity measure in overlap measurement vector
   */
  sealed abstract class Measure(i: Int) { def toInt: Int = i }

  /**
   * Jaccard similarity index
   */
  case object JSI extends Measure(0)

  /**
   * Dice similarity coefficient
   */
  case object DSC extends Measure(1)

  /**
   * Implicit conversion of Overlap.Measure case object to Int
   * @param m Overlap measure enumeration case object
   * @return IRTK.labelStats array index
   */
  implicit def measureToInt(m: Measure): Int = m.toInt

  /**
   * Names of label groups for which the overlap measures are computed
   */
  val groups = getStringListProperty("labels")

  /**
   * Union of all label numbers comprising the label groups for which the overlap is computed
   * in the order specified by Label.number (i.e., the "Label Number" column of the input CSV file)
   */
  lazy val numbers: List[Int] = {
    val union = Label.groupName.filter(groups.contains(_)).foldLeft(Set[Int]()) {
      (b, a) => b | Label.group(a)
    }
    Label.number.map(label => if (union.contains(label)) Some(label) else None).flatten.toList
  }

  /**
   * Compute overlap measures for each label
   * @param a Segmentation a.
   * @param b Segmentation b.
   * @return A map from label number to a vector of different overlap measures computed from the same voxel counts
   *         to avoid having to iterate over segmentation images multiple times if more than one measure is needed.
   */
  def apply(a: File, b: File, which: Measure = DSC) = new Overlap(a, b, which)

  /**
   * Create new overlap measure object
   * @param stats Label statistics compute by IRTK.labelStats
   * @param which Which overlap measure to extract
   */
  def apply(stats: Map[Int, Array[Double]], which: Measure) = new Overlap(stats, which)
}


/**
 * Registration overlap measures
 * @param stats Label statistics compute by IRTK.labelStats
 * @param which Which overlap measure to extract
 */
class Overlap(stats: Map[Int, Array[Double]], which: Overlap.Measure = Overlap.DSC) {

  /**
   * Create object of overlap measures comparing two segmentation images
   * @param a Segmentation a
   * @param b Segmentation b
   * @param which Which overlap measure to extract
   */
  def this(a: File, b: File, which: Overlap.Measure) = {
    this(IRTK.labelStats(a, b, Some(Overlap.numbers.toSet)))
  }

  /**
   * Get label overlap measure value
   * @param label Label number
   * @return Overlap measure value of specified label
   */
  def apply(label: Int): Double = stats(label)(which)

  /**
   * Get map from label to specified overlap measure
   * @return Map from label number to overlap measure
   */
  def toMap: Map[Int, Double] = stats.map { case (label, measure) => label -> measure(which) }

  /**
   * Get array containing specified overlap measure values
   * @return Array of overlap measures in the order specified by Label.number
   */
  def toArray: Array[Double] = Overlap.numbers.map(label => stats(label)(which)).toArray

  /**
   * Mean of overlap measure for each label group
   * @return Map from label group name to mean of overlap measure
   */
  lazy val mean: Map[String, Double] = Overlap.groups.map {
    group => {
      var mean   = .0
      val labels = if (group.toLowerCase == "all") Overlap.numbers else Label.group(group)
      labels.foreach( label => mean += stats(label)(which) )
      if (labels.size > 0) mean /= labels.size
      group -> mean
    }
  }.toMap

  /**
   * Compute mean of overlap measure for each label group
   * @return Array of mean overlap measure for each label group in the order of Overlap.groups
   */
  def getMeanValues: Array[Double] = {
    Overlap.groups.map(group => mean(group)).toArray
  }
}