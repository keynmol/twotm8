import bindgen.interface.Binding
import scala.scalanative.build.Mode
import scala.scalanative.build.LTO
import scala.sys.process
import java.nio.file.Paths

Global / onChangedBuildSource := ReloadOnSourceChanges

val Versions = new {
  val Scala = "3.2.0"
  val SNUnit = "0.0.24"
  val Tapir = "1.1.0"
  val upickle = "2.0.0"
  val scribe = "3.10.3"
  val Laminar = "0.14.2"
  val scalajsDom = "2.3.0"
  val waypoint = "0.5.0"
  val scalacss = "1.0.0"
}

lazy val root = project.in(file(".")).aggregate(frontend, app)

lazy val manage =
  project
    .in(file("manage"))
    .enablePlugins(ScalaNativePlugin, VcpkgPlugin)
    .dependsOn(bindings)
    .settings(environmentConfiguration)
    .settings(vcpkgNativeConfig())
    .settings(
      scalaVersion := Versions.Scala,
      vcpkgDependencies := Set("libpq", "openssl"),
      libraryDependencies += "com.outr" %%% "scribe" % Versions.scribe,
      libraryDependencies += "com.lihaoyi" %%% "upickle" % Versions.upickle
    )

lazy val shared =
  crossProject(NativePlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .settings(
      scalaVersion := Versions.Scala,
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.tapir" %%% "tapir-json-upickle" % Versions.Tapir,
        "com.softwaremill.sttp.tapir" %%% "tapir-core" % Versions.Tapir
      )
    )

lazy val frontend =
  project
    .in(file("frontend"))
    .enablePlugins(ScalaJSPlugin)
    .settings(
      scalaJSUseMainModuleInitializer := true,
      scalaVersion := Versions.Scala,
      libraryDependencies ++= Seq(
        "com.raquo" %%% "laminar" % Versions.Laminar,
        "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
        "com.raquo" %%% "waypoint" % Versions.waypoint,
        "com.lihaoyi" %%% "upickle" % Versions.upickle,
        "com.github.japgolly.scalacss" %%% "core" % Versions.scalacss
      )
    )

lazy val app =
  project
    .in(file("app"))
    .dependsOn(bindings)
    .enablePlugins(ScalaNativePlugin, VcpkgPlugin)
    .settings(environmentConfiguration)
    .settings(vcpkgNativeConfig())
    .settings(
      scalaVersion := Versions.Scala,
      vcpkgDependencies := Set("libpq", "openssl", "libidn2"),
      libraryDependencies += "com.softwaremill.sttp.model" %%% "core" % "1.5.2",
      libraryDependencies += "com.outr" %%% "scribe" % Versions.scribe,
      libraryDependencies += "com.lihaoyi" %%% "upickle" % Versions.upickle,
      libraryDependencies += "com.github.lolgab" %%% "snunit-tapir" % Versions.SNUnit,
      libraryDependencies += "com.eed3si9n.verify" %%% "verify" % "1.0.0" % Test,
      libraryDependencies += "com.github.lolgab" %%% "scala-native-crypto" % "0.0.3" % Test,
      testFrameworks += new TestFramework("verify.runner.Framework")
    )
    .dependsOn(shared.native)

lazy val environmentConfiguration = Seq(nativeConfig := {
  val conf = nativeConfig.value
  if (sys.env.get("SN_RELEASE").contains("fast"))
    conf.withOptimize(true).withLTO(LTO.thin).withMode(Mode.releaseFast)
  else conf
})

lazy val bindings =
  project
    .in(file("bindings"))
    .enablePlugins(ScalaNativePlugin, BindgenPlugin, VcpkgPlugin)
    .settings(
      scalaVersion := Versions.Scala,
      resolvers += Resolver.sonatypeRepo("snapshots"),
      // Generate bindings to Postgres main API
      vcpkgDependencies := Set("libpq", "openssl"),
      Compile / bindgenBindings ++= Seq(
        Binding(
          vcpkgManager.value.includes("libpq") / "libpq-fe.h",
          "libpq",
          linkName = Some("pq"),
          cImports = List("libpq-fe.h"),
          clangFlags = vcpkgConfigurator.value
            .updateCompilationFlags(List("-std=gnu99"), "libpq")
            .toList
        ),
        Binding(
          (Compile / baseDirectory).value / "openssl-amalgam.h",
          "openssl",
          cImports = List("openssl/sha.h", "openssl/evp.h"),
          clangFlags = List("-I" + vcpkgManager.value.includes("openssl"))
        )
      )
    )
    .settings(vcpkgNativeConfig())

val buildApp = taskKey[Unit]("")
buildApp := {
  buildBackend.value
  buildFrontend.value
}

val buildBackend = taskKey[Unit]("")
buildBackend := {
  val target = (app / Compile / nativeLink).value

  val destination = (ThisBuild / baseDirectory).value / "build"

  IO.copyFile(
    target,
    destination / "twotm8",
    preserveExecutable = true,
    preserveLastModified = true
  )

  restartLocalUnit
}

def restartLocalUnit = {
  // `unitd --help` prints the default unix socket
  val unixSocketPath = process
    .Process(Seq("unitd", "--help"))
    .!!
    .linesIterator
    .find(_.contains("unix:"))
    .get
    .replaceAll(".+unix:", "")
    .stripSuffix("\"")

  val f = new File(unixSocketPath)

  if (f.exists()) {
    val cmd =
      s"curl --unix-socket $unixSocketPath http://localhost/control/applications/app/restart"

    println(process.Process(cmd).!!)
  }
}

lazy val frontendFile = taskKey[File]("")
frontendFile := {
  if (sys.env.get("SN_RELEASE").contains("fast"))
    (frontend / Compile / fullOptJS).value.data
  else
    (frontend / Compile / fastOptJS).value.data
}

lazy val buildFrontend = taskKey[Unit]("")
buildFrontend := {
  val js = frontendFile.value
  val destination = (ThisBuild / baseDirectory).value / "build"

  IO.write(
    destination / "index.html",
    """
      <!DOCTYPE html>
      <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <meta http-equiv="X-UA-Compatible" content="ie=edge">
          <title>Twotm8 - a place for thought leaders to thought lead</title>
        </head>
        <body>
        <div id="appContainer"></div>
        <script src="/frontend.js"></script>
        </body>
      </html>
    """.stripMargin
  )

  IO.copyFile(js, destination / "frontend.js")

  restartLocalUnit
}

def vcpkgNativeConfig(rename: String => String = identity) = Seq(
  nativeConfig := {
    import com.indoorvivants.detective.Platform
    val configurator = vcpkgConfigurator.value
    val manager = vcpkgManager.value
    val conf = nativeConfig.value
    val deps = vcpkgDependencies.value.toSeq.map(rename)

    val files = deps.map(d => manager.files(d))

    val compileArgsApprox = files.flatMap { f =>
      List("-I" + f.includeDir.toString)
    }
    val linkingArgsApprox = files.flatMap { f =>
      List("-L" + f.libDir) ++ f.staticLibraries.map(_.toString)
    }

    import scala.util.control.NonFatal

    def updateLinkingFlags(current: Seq[String], deps: String*) =
      try {
        configurator.updateLinkingFlags(
          current,
          deps*
        )
      } catch {
        case NonFatal(exc) =>
          linkingArgsApprox
      }

    def updateCompilationFlags(current: Seq[String], deps: String*) =
      try {
        configurator.updateCompilationFlags(
          current,
          deps*
        )
      } catch {
        case NonFatal(exc) =>
          compileArgsApprox
      }

    val arch64 =
      if (
        Platform.arch == Platform.Arch.Arm && Platform.bits == Platform.Bits.x64
      )
        List("-arch", "arm64")
      else Nil

    conf
      .withLinkingOptions(
        updateLinkingFlags(
          conf.linkingOptions ++ arch64,
          deps*
        )
      )
      .withCompileOptions(
        updateCompilationFlags(
          conf.compileOptions ++ arch64,
          deps*
        )
      )
  }
)
