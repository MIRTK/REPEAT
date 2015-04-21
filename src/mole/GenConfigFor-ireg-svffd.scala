// =====================================================================================================================
// Registration Performance Assessment Tool (REPEAT)
// OpenMOLE script to generate CSV file with arguments for ireg with SV FFD model (IRTK 3)
//
// Copyright (C) 2015  Andreas Schuh
//
//   This program is free software: you can redistribute it and/or modify
//   it under the terms of the GNU Affero General Public License as published by
//   the Free Software Foundation, either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU Affero General Public License for more details.
//
//   You should have received a copy of the GNU Affero General Public License
//   along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
// =====================================================================================================================

val csvFile = new java.io.File("Config/ireg-svffd.csv")

if (csvFile.exists) {
  csvFile.delete()
} else {
  val csvDir = csvFile.getParentFile
  if (csvDir != null) csvDir.mkdirs()
}

val im  = Val[String] // Integration method
val ds  = Val[Double] // Control point spacing
val be  = Val[Double] // Bending energy weight
val bch = Val[Int]    // No. of BCH terms

val sampling = {
  (im  in List("FastSS", "SS", "RKE1", "RK4")) x
  (ds  in List(2.5)) x
  (be  in List(.0, .0001, .0005, .001, .005, .01, .05)) x
  (bch in List(0, 4))
}

val args = Val[String]
val writeArgsToCsv = ScalaTask(
  s"""
    |val args = s\"\"\"
    |-par "Integration method = $$im"
    |-par "Control point spacing = $$ds"
    |-par "Bending energy weight = $$be"
    |-par "No. of BCH terms = $$bch"
    |\"\"\".replaceAll("\\n", " ").trim
  """.stripMargin) set (
    inputs  += (im, ds, be, bch),
    outputs += args
  ) hook (
    AppendToCSVFileHook(argsCsvPath, args) set (csvHeader := "Command Arguments")
  )

val exec = ExplorationTask(sampling) -< writeArgsToCsv start
exec.waitUntilEnded