organization := "com.andreasschuh"

name := "repeat"

version := "0.1"

scalaVersion := "2.11.6"

scalacOptions ++= Seq("-optimize", "-feature", "-deprecation")

osgiSettings

OsgiKeys.exportPackage := Seq("com.andreasschuh.*")

OsgiKeys.importPackage := Seq("*")

OsgiKeys.privatePackage := Seq("")

scalariformSettings

resolvers += "ISC-PIF Release" at "http://maven.iscpif.fr/public/"

val openMOLEVersion = "5.0-SNAPSHOT"

libraryDependencies += "org.openmole" %% "org-openmole-core-dsl" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-core-workspace" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-core-workflow" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-task-scala" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-environment-condor" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-environment-ssh" % openMOLEVersion

libraryDependencies += "org.openmole" %% "org-openmole-plugin-environment-slurm" % openMOLEVersion
