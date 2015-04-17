package com.andreasschuh.repeat

/**
 * Contains information about segmentation labels and groups
 */
object Label extends Configurable("segmentation") {

  val info  = Csv.fromFile(getFileProperty("labels"))
  val nrows = info("Label Number").length

  /**
   * All label numbers
   */
  val number = info("Label Number").map(_.toInt)

  /**
   * Names of all labels
   */
  val name = {
    val name = info("Label Name")
    for (i <- 0 until nrows) yield number(i) -> name(i)
  }.toMap

  /**
   * Prefix of label group header
   */
  val groupPrefix = "Label Group: "

  /**
   * Names of label groups
   */
  lazy val groupName = info.keySet.filter(_.startsWith(groupPrefix)).map(_.drop(groupPrefix.length))

  /**
   * Set of label numbers comprising each label group
   */
  lazy val group = groupName.map {
    nameOfGroup => nameOfGroup -> {
      val selected = info(groupPrefix + nameOfGroup).map(_.toLowerCase)
      for (i <- 0 until nrows) yield if (selected(i) == "+" || selected(i) == "yes") Some(number(i)) else None
    }.flatten.toSet
  }.toMap

  /**
   * Set of all label numbers
   */
  def ids = number.toSet

}
