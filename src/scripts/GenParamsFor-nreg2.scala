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

val csvFile = new java.io.File(if (args.length > 0) args(0) else "Config/nreg2.csv")

if (csvFile.exists) {
  csvFile.delete()
} else {
  val csvDir = csvFile.getParentFile
  if (csvDir != null) csvDir.mkdirs()
}

val i = Val[Int]
val ID = Val[String]

val InitialControlPointSpacing = Val[Double]
val Lambda1 = Val[Double]
val Lambda2 = Val[Double]
val Lambda3 = Val[Double]
val NoOfResolutionLevels = Val[Int]
val Epsilon = Val[Double]
val SimilarityMeasure = Val[String]
val NoOfBins = Val[Int]
val InterpolationMode = Val[String]
val OptimizationMethod = Val[String]
val NoOfIterations = Val[Int]

val params = {
  (InitialControlPointSpacing in List(20.0)) x
  (Lambda1 in List(.0, .000001, .000005, .00001, .00005, .0001, .0005, .001, .005)) x
  (Lambda2 in List(.0)) x
  (Lambda3 in List(.0)) x
  (NoOfResolutionLevels in List(4)) x
  (Epsilon in List(.0, .0001)) x
  (SimilarityMeasure in List("NMI")) x
  (NoOfBins in List(64)) x
  (InterpolationMode in List("Linear")) x
  (OptimizationMethod in List("GradientDescent")) x
  (NoOfIterations in List(40, 100))
}

val append =
  AppendToCSVFileHook(csvFile,
    ID.toArray,
    InitialControlPointSpacing.toArray,
    Lambda1.toArray,
    Lambda2.toArray,
    Lambda3.toArray,
    NoOfResolutionLevels.toArray,
    Epsilon.toArray,
    SimilarityMeasure.toArray,
    NoOfBins.toArray,
    InterpolationMode.toArray,
    OptimizationMethod.toArray,
    NoOfIterations.toArray
  )

val sample = ExplorationTask(params zipWithIndex i)
val setID  = Capsule(ScalaTask("""val ID = f"${i + 1}%02d" """) set (inputs += i, outputs += ID), strainer = true)
val write  = Capsule(EmptyTask(), strainer = true) hook append
val exec   = (sample -< setID >- write).start.waitUntilEnded()

