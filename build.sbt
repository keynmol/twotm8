import bindgen.interface.Binding
import scala.scalanative.build.Mode
import scala.scalanative.build.LTO
import scala.sys.process
import java.nio.file.Paths

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = project
  .in(file("."))
  .aggregate(frontend.projectRefs*)
  .aggregate(app.projectRefs*)
  .aggregate(shared.projectRefs*)
  .aggregate(tests.projectRefs*)
  .aggregate(bindings.projectRefs*)

lazy val shared =
  projectMatrix
    .in(file("shared"))
    .jvmPlatform(Seq(Versions.Scala))
    .jsPlatform(Seq(Versions.Scala))
    .nativePlatform(Seq(Versions.Scala))
    .settings(
      scalaVersion := Versions.Scala,
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.tapir" %%% "tapir-json-upickle" % Versions.Tapir,
        "com.softwaremill.sttp.tapir" %%% "tapir-core" % Versions.Tapir
      )
    )

lazy val frontend =
  projectMatrix
    .in(file("frontend"))
    .jsPlatform(Seq(Versions.Scala))
    .settings(
      scalaJSUseMainModuleInitializer := true,
      scalaVersion := Versions.Scala,
      libraryDependencies ++= Seq(
        "com.github.japgolly.scalacss" %%% "core" % Versions.scalacss,
        "com.lihaoyi" %%% "upickle" % Versions.upickle,
        "com.raquo" %%% "laminar" % Versions.Laminar,
        "com.raquo" %%% "waypoint" % Versions.waypoint,
        "com.softwaremill.retry" %%% "retry" % Versions.sttpRetry,
        "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % Versions.Tapir,
        "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom
      )
    )
    .dependsOn(shared)

lazy val tests =
  projectMatrix
    .in(file("tests"))
    .dependsOn(shared)
    .jvmPlatform(
      Seq(Versions.Scala),
      Seq.empty,
      _.settings(
        libraryDependencies ++= Seq(
          "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % Versions.Tapir % Test,
          "org.http4s" %% "http4s-ember-client" % Versions.Http4s % Test,
          "com.github.jwt-scala" %% "jwt-upickle" % Versions.jwt % Test
        )
      )
    )
    .jsPlatform(Seq(Versions.Scala))
    .nativePlatform(Seq(Versions.Scala))
    .settings(
      libraryDependencies ++= Seq(
        "com.disneystreaming" %%% "weaver-cats" % Versions.weaver % Test
      ),
      testFrameworks += new TestFramework("weaver.framework.CatsEffect")
    )

lazy val set =
  tests
    .native(Versions.Scala)
    .dependsOn(app.native(Versions.Scala))
    .settings(
      libraryDependencies +=
        "com.github.lolgab" %%% "scala-native-crypto" % Versions.scalaNativeCrypto % Test
    )

lazy val app =
  projectMatrix
    .in(file("app"))
    .nativePlatform(Seq(Versions.Scala))
    .dependsOn(bindings, shared)
    .enablePlugins(VcpkgPlugin)
    .settings(environmentConfiguration)
    .settings(vcpkgNativeConfig())
    .settings(
      scalaVersion := Versions.Scala,
      vcpkgRootInit := com.indoorvivants.vcpkg.VcpkgRootInit.SystemCache(),
      vcpkgDependencies := Set("libpq", "openssl", "libidn2"),
      libraryDependencies ++= Seq(
        "com.github.lolgab" %%% "scala-native-crypto" % Versions.scalaNativeCrypto % Test,
        "com.github.lolgab" %%% "snunit-tapir" % Versions.SNUnit,
        "com.indoorvivants.roach" %%% "core" % Versions.Roach,
        "com.lihaoyi" %%% "upickle" % Versions.upickle,
        "com.outr" %%% "scribe" % Versions.scribe
      ),
      nativeConfig ~= (_.withEmbedResources(true).withDump(true))
    )

lazy val bindings =
  projectMatrix
    .in(file("bindings"))
    .nativePlatform(Seq(Versions.Scala))
    .enablePlugins(BindgenPlugin, VcpkgPlugin)
    .settings(
      scalaVersion := Versions.Scala,
      resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
      vcpkgRootInit := com.indoorvivants.vcpkg.VcpkgRootInit.SystemCache(),
      // Generate bindings to Postgres main API
      vcpkgDependencies := Set("openssl"),
      Compile / bindgenBindings ++= Seq(
        Binding(
          (ThisBuild / baseDirectory).value / "bindings" / "openssl-amalgam.h",
          "openssl",
          cImports = List("openssl/sha.h", "openssl/evp.h"),
          clangFlags = List("-I" + vcpkgConfigurator.value.includes("openssl"))
        )
      )
    )
    .settings(vcpkgNativeConfig())

addCommandAlias("integrationTests", "tests3/test")

val Versions = new {
  val Scala = "3.2.1"

  val SNUnit = "0.2.4"

  val Tapir = "1.2.3"

  val upickle = "2.0.0"

  val scribe = "3.10.5"

  val Laminar = "0.14.5"

  val scalajsDom = "2.3.0"

  val waypoint = "6.0.0"

  val scalacss = "1.0.0"

  val Roach = "0.0.3"

  val sttpRetry = "0.3.6"

  val scalaNativeCrypto = "0.0.4"

  val weaver = "0.8.1"

  val Http4s = "0.23.16"

  val jwt = "9.1.2"
}

lazy val environmentConfiguration = Seq(nativeConfig := {
  val conf = nativeConfig.value
  if (sys.env.get("SN_RELEASE").contains("fast"))
    conf.withOptimize(true).withLTO(LTO.thin).withMode(Mode.releaseFast)
  else conf
})

val buildApp = taskKey[Unit]("")
buildApp := {
  buildBackend.value
  buildFrontend.value
}

val buildBackend = taskKey[Unit]("")
buildBackend := {
  val target = (app.native(Versions.Scala) / Compile / nativeLink).value

  val destination = (ThisBuild / baseDirectory).value / "build" / "twotm8"

  IO.copyFile(
    target,
    destination,
    preserveExecutable = true,
    preserveLastModified = true
  )

  process.Process(s"chmod 0777 ${destination}").!!

  sys.env.get("CI").foreach { _ =>
    val sudo = if (sys.env.contains("USE_SUDO")) "sudo " else ""
  /* process.Process(s"${sudo}chown unit ${destination}").!! */
  /* process.Process(s"${sudo}chgrp unit ${destination}").!! */
  }
}

def unitConfig(buildPath: File) =
  s"""
{
  "listeners": {
    "*:8080": {
      "pass": "routes"
    }
  },
  "routes": [
    {
      "match": {
        "uri": "/api/*"
      },
      "action": {
        "pass": "applications/app"
      }
    },
    {
      "match": {
        "uri": "~^((/(.*)\\\\.(js|css|html))|/)$$"
      },
      "action": {
        "share": "${buildPath}$$uri"
      }
    },
    {
      "action": {
        "share": "${buildPath / "index.html"}"
      }
    }
  ],
  "applications": {
    "app": {
      "processes": {
        "max": 50,
        "spare": 2,
        "idle_timeout": 180
      },
      "type": "external",
      "executable": "${buildPath / "twotm8"}",
      ${sys.env
      .get("CI")
      .map { _ =>
        """
        "user": "runner",
        "group": "docker",
        """
      }
      .getOrElse("")}
      "environment": {
        "JWT_SECRET": "secret"
      },
      "limits": {
        "timeout": 1,
        "requests": 1000
      }
    }
  }
}

"""

lazy val deployLocally = taskKey[Unit]("")
deployLocally := {
  locally { buildApp.value }
  locally { updateUnitConfiguration.value }
}

lazy val updateUnitConfiguration = taskKey[Unit]("")

updateUnitConfiguration := {
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

  assert(
    f.exists(),
    s"Expected Unix socket file for Nginx Unit `${f}` to exist"
  )

  sLog.value.info(s"Unit socket path: $unixSocketPath")

  sLog.value.info(buildBackend.value.toString)

  val configJson = writeConfig.value

  val sudo = if (sys.env.contains("USE_SUDO")) "sudo " else ""

  val cmd_create =
    s"${sudo}curl -s -X PUT --data-binary @$configJson --unix-socket $unixSocketPath http://localhost/config"
  val cmd =
    s"${sudo}curl -s --unix-socket $unixSocketPath http://localhost/control/applications/app/restart"

  val create_result = process.Process(cmd_create).!!
  val reload_result = process.Process(cmd).!!

  assert(
    create_result.contains("Reconfiguration done"),
    s"Unit reconfiguration didn't succeed, returning `$create_result`"
  )
  assert(
    reload_result.contains("success"),
    s"Unit reload didn't succeed, returning `$reload_result`"
  )
}

lazy val writeConfig = taskKey[File]("")
writeConfig := {
  val buildPath = (ThisBuild / baseDirectory).value / "build"
  val path = buildPath / "config.json"

  IO.write(path, unitConfig(buildPath))

  path
}

lazy val frontendFile = taskKey[File]("")
frontendFile := {
  if (sys.env.get("SN_RELEASE").contains("fast"))
    (frontend.js(Versions.Scala) / Compile / fullOptJS).value.data
  else
    (frontend.js(Versions.Scala) / Compile / fastOptJS).value.data
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
}

def vcpkgNativeConfig(rename: String => String = identity) = Seq(
  nativeConfig := {
    import com.indoorvivants.detective.Platform
    val configurator = vcpkgConfigurator.value
    val conf = nativeConfig.value
    val deps = vcpkgDependencies.value.toSeq.map(rename)

    val files = deps.map(d => configurator.files(d))

    val compileArgsApprox = files.flatMap { f =>
      List("-I" + f.includeDir.toString)
    }
    val linkingArgsApprox = files.flatMap { f =>
      List("-L" + f.libDir) ++ f.staticLibraries.map(_.toString)
    }

    import scala.util.control.NonFatal

    def updateLinkingFlags(current: Seq[String], deps: String*) =
      try {
        configurator.pkgConfig.updateLinkingFlags(
          current,
          deps*
        )
      } catch {
        case NonFatal(exc) =>
          linkingArgsApprox
      }

    def updateCompilationFlags(current: Seq[String], deps: String*) =
      try {
        configurator.pkgConfig.updateCompilationFlags(
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
