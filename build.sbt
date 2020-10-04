import Dependencies._
import org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings

name := "downloadmanager"
ThisBuild / scalaVersion := "2.13.3"
ThisBuild / organization := "com.dhawan"
ThisBuild / organizationName := "Dhawan"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.4.0"
ThisBuild / version := "1.0.0"

Global / cancelable := false

libraryDependencies ++= deps

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

val excludedWarts = Seq(Wart.Any, Wart.Nothing, Wart.PublicInference)

wartremoverErrors in (Compile, compile) ++= Warts.allBut(excludedWarts: _*)

scalacOptions ++=
  Seq(
    "-Xfatal-warnings",
    "-encoding",
    "utf-8",
    "-Yrangepos",
    "-Wunused",
    "-Xlint",
    "-language:implicitConversions",
    "-Xcheckinit",
    "-unchecked",
    "-deprecation",
    "-explaintypes",
    "-Wdead-code",
    "-Wextra-implicit",
    "-Wnumeric-widen",
    "-Ymacro-annotations",
    // silence unwanted warning: https://github.com/scala/bug/issues/12072
    "-Wconf:cat=lint-byname-implicit:silent"
  )

addCommandAlias("lint", ";scalafmtAll; scalafmtSbt; scalafix; test:scalafix;")
