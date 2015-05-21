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

val csvFile = new java.io.File(if (args.length > 0) args(0) else "Config/ireg-ffd.csv")

if (csvFile.exists) {
  csvFile.delete()
} else {
  val csvDir = csvFile.getParentFile
  if (csvDir != null) csvDir.mkdirs()
}

val i  = Val[Int]    // Index of parameter set
val ID = Val[String] // ID of parameter set
val ControlPointSpacing = Val[Double] // Control point spacing
val BendingEnergyWeight = Val[Double] // Bending energy weight

val params = {
  (ControlPointSpacing in List(2.5)) x
  (BendingEnergyWeight in List(.0, .0001, .0005, .001, .005, .01, .05))
}

val append =
  AppendToCSVFileHook(csvFile,
    ID.toArray,
    ControlPointSpacing.toArray,
    BendingEnergyWeight.toArray
  )

val sample = ExplorationTask(params zipWithIndex i)
val setID  = Capsule(ScalaTask("""val ID = f"${i + 1}%02d" """) set (inputs += i, outputs += ID), strainer = true)
val write  = Capsule(EmptyTask(), strainer = true) hook append
val exec   = (sample -< setID >- write).start.waitUntilEnded()
