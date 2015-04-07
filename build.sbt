organization := "com.andreasschuh"

name := "repeat"

version := "0.1"

scalaVersion := "2.11.6"

scalacOptions ++= Seq("-optimize", "-feature", "-deprecation")

libraryDependencies += "com.typesafe" % "config" % "1.2.1"

osgiSettings

OsgiKeys.exportPackage := Seq("com.andreasschuh.*", "com.typesafe.config")

OsgiKeys.importPackage := Seq("*;resolution:=optional")

OsgiKeys.privatePackage := Seq("!scala.*", "*")

scalariformSettings
