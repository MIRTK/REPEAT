package com.andreasschuh.repeat.core

/**
 * Contains information about segmentation labels and groups
 */
object Segmentation extends Configurable("segmentation") {

  protected val info  = Csv.fromFile(getFileProperty("csv"))
  protected val nrows = info("Label Number").length

  /**
   * All label numbers
   */
  val labels = info("Label Number").map(_.toInt)

  /**
   * Names of all labels
   */
  val names = {
    val name = info("Label Name")
    for (i <- 0 until nrows) yield labels(i) -> name(i)
  }.toMap

  /**
   * Prefix of label group header
   */
  val groupPrefix = "Label Group: "

  /**
   * Names of label groups
   */
  lazy val groupNames = info.keySet.filter(_.startsWith(groupPrefix)).map(_.drop(groupPrefix.length))

  /**
   * Set of label numbers comprising each label group
   */
  lazy val groupLabels = groupNames.map {
    groupName => groupName -> {
      val selected = info(groupPrefix + groupName).map(_.toLowerCase)
      for (i <- 0 until nrows) yield if (selected(i) == "+" || selected(i) == "yes") Some(labels(i)) else None
    }.flatten.toSet
  }.toMap

  /**
   * Set of all label numbers
   */
  def labelSet = labels.toSet

}
