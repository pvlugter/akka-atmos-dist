
import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, distBootClass, distJvmOptions, outputDirectory }

object HelloKernelBuild extends Build {
  val Organization = "akka.sample"
  val Version      = "2.2.0"
  val ScalaVersion = "2.10.2"

  lazy val HelloKernel = Project(
    id = "hello-kernel",
    base = file("."),
    settings = defaultSettings ++ AkkaKernelPlugin.distSettings ++ AtmosDist.settings ++ Seq(
      libraryDependencies ++= Dependencies.helloKernel,
      distJvmOptions in Dist := "-Xms256M -Xmx1024M",
      distBootClass in Dist := "sample.kernel.hello.HelloKernel",
      outputDirectory in Dist := file("target/hello-dist")
    )
  )

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := Organization,
    version      := Version,
    scalaVersion := ScalaVersion,
    crossPaths   := false,
    organizationName := "Typesafe Inc.",
    organizationHomepage := Some(url("http://www.typesafe.com"))
  )

  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

  )
}

object Dependencies {
  import Dependency._

  val helloKernel = Seq(
    akkaKernel, akkaSlf4j, logback
  )
}

object Dependency {
  // Versions
  object V {
    val Akka      = "2.2.0"
  }

  val akkaKernel = "com.typesafe.akka" %% "akka-kernel" % V.Akka
  val akkaSlf4j  = "com.typesafe.akka" %% "akka-slf4j"  % V.Akka
  val logback    = "ch.qos.logback"    % "logback-classic" % "1.0.0"
}

object AtmosDist {
  import Dependency._
  import AkkaKernelPlugin.{ dist, distConfig, DistConfig }

  val AtmosVersion   = "1.2.0"
  val AspectjVersion = "1.7.2"

  val Atmos = config("atmos").hide

  val atmosTrace    = "com.typesafe.atmos" %% ("trace-akka-" + V.Akka) % AtmosVersion
  val aspectjWeaver = "org.aspectj"         % "aspectjweaver"          % AspectjVersion % Atmos.name
  val sigarLibs     = "com.typesafe.atmos"  % "atmos-sigar-libs"       % AtmosVersion   % Atmos.name

  def dependencies = Seq(atmosTrace, aspectjWeaver, sigarLibs)

  def settings: Seq[Setting[_]] = Seq(
    ivyConfigurations += AtmosDist.Atmos,
    libraryDependencies ++= AtmosDist.dependencies,
    dist in Dist <<= addAtmosToDist
  )

  def addAtmosToDist = (dist in Dist, distConfig in Dist, update, streams) map { (dist, config, report, s) =>
    s.log.info("Adding Atmos to distribution...")

    val lib = dist / "lib"
    report.matching(moduleFilter(name = "aspectjweaver")).headOption map { jar =>
      IO.copyFile(jar, lib / "weaver" / "aspectjweaver.jar")
    }
    report.matching(moduleFilter(name = "atmos-sigar-libs")).headOption map { jar =>
      IO.unzip(jar, lib / "sigar")
    }
    s.log.info("Atmos libs added to distribution.")

    val start = dist / "bin" / "start-with-atmos"
    IO.write(start, startScript(config))
    start.setExecutable(true, false)
    s.log.info("Atmos start script added to distribution.")

    dist
  }

  def startScript(config: DistConfig) = {
    "#!/bin/sh\n\n" +
    "AKKA_HOME=\"$(cd \"$(cd \"$(dirname \"$0\")\"; pwd -P)\"/..; pwd)\"\n\n" + Seq(
      "java",
      config.distJvmOptions,
      "-javaagent:\"$AKKA_HOME/lib/weaver/aspectjweaver.jar\"",
      "-classpath \"$AKKA_HOME/config:$AKKA_HOME/lib/*\"",
      "-Dorg.hyperic.sigar.path=\"$AKKA_HOME/lib/sigar\"",
      "-Dakka.home=\"$AKKA_HOME\"",
      config.distMainClass,
      config.distBootClass,
      "\"$@\""
    ).mkString(" \\\n")
  }
}
