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

package com.andreasschuh.repeat.puzzle

//import java.io.File
//import java.nio.file.Path
//import scala.language.reflectiveCalls
//
//import org.openmole.core.dsl._
//import org.openmole.core.workflow.mole.Capsule
//import org.openmole.core.workflow.transition.Condition
//import org.openmole.plugin.grouping.batch._
//import org.openmole.plugin.sampling.combine._
//import org.openmole.plugin.sampling.csv._
//import org.openmole.plugin.task.scala._
//import org.openmole.plugin.tool.pattern.{Switch, Case}
//
//import com.andreasschuh.repeat.core.{Environment => Env, _}
//
//
///**
// * Workflow puzzle for evaluation of sharpness of intensity-average image
// */
//object EvaluateIntensityVariance extends Configurable("evaluation.intensity-average") {
//
//  /** Whether to evaluate sharpness of intensity-average */
//  val enabled = getBooleanProperty("enabled")
//
//  /** Output directory of voxel-wise intensity variance maps */
//  val outDir = FileUtil.normalize(FileUtil.join(Workspace.dir, getStringProperty(".workspace.output.iv-maps")))
//
//  /** Get workflow puzzle for evaluation of intensity-average sharpness of specified registration */
//  def apply(reg: Registration) = new EvaluateIntensityVariance(reg)
//}
//
///**
// * Evaluate sharpness of voxel-wise intensity-average image
// *
// * @param reg Registration to be evaluated.
// */
//class EvaluateIntensityVariance(reg: Registration) {
//
//
//}
