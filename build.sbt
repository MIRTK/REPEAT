organization := "com.andreasschuh"

name := "repeat"

version := "0.1"

scalaVersion := "2.11.6"

scalacOptions ++= Seq("-optimize", "-feature", "-deprecation", "-Yinline-warnings")

osgiSettings

OsgiKeys.exportPackage := Seq("com.andreasschuh.repeat.*")

OsgiKeys.importPackage := Seq("*")

OsgiKeys.privatePackage := Seq("")

defaultScalariformSettings

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "ISC-PIF Release" at "http://maven.iscpif.fr/public/"

val openMOLEVersion = "5.0-SNAPSHOT"

libraryDependencies += "org.openmole" %% "org-openmole-core-dsl" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-core-macros" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-core-workspace" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-core-workflow" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-task-scala" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-domain-collection" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-domain-file" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-grouping-batch" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-environment-condor" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-environment-ssh" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-environment-slurm" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-hook-display" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-hook-file" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-sampling-combine" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-sampling-csv" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-source-file" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-tool-pattern" % openMOLEVersion
