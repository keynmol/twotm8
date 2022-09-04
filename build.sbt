import bindgen.interface.Binding
import scala.scalanative.build.Mode
import scala.scalanative.build.LTO
import scala.sys.process
import bindgen.interface.Platform
import bindgen.interface.LogLevel
import java.nio.file.Paths

Global / onChangedBuildSource := ReloadOnSourceChanges

val Versions = new {
  val Scala = "3.1.1"
  val SNUnit = "0.0.15"
  val upickle = "1.6.0"
  val scribe = "3.8.1"
  val Laminar = "0.14.2"
  val scalajsDom = "2.1.0"
  val waypoint = "0.5.0"
  val scalacss = "1.0.0"
}

lazy val manage =
  project
    .in(file("manage"))
    .dependsOn(postgres, openssl)
    .configure(config)
    .settings(
      libraryDependencies += "com.outr" %%% "scribe" % Versions.scribe,
      libraryDependencies += "com.lihaoyi" %%% "upickle" % Versions.upickle
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
    .dependsOn(postgres, openssl)
    .configure(config)
    .settings(
      testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-s", "-v"),
      libraryDependencies += "com.outr" %%% "scribe" % Versions.scribe,
      libraryDependencies += "com.lihaoyi" %%% "upickle" % Versions.upickle,
      libraryDependencies += "com.github.lolgab" %%% "snunit" % Versions.SNUnit,
      libraryDependencies += "com.eed3si9n.verify" %%% "verify" % "1.0.0" % Test,
      testFrameworks += new TestFramework("verify.runner.Framework"),
      libraryDependencies += (
        "com.github.lolgab" %%% "snunit-routes" % Versions.SNUnit cross CrossVersion.for3Use2_13
      ).excludeAll(
        ExclusionRule("org.scala-native"),
        ExclusionRule("com.github.lolgab", "snunit_native0.4_2.13")
      )
    )

lazy val demoApp =
  project
    .in(file("demo-app"))
    .dependsOn(postgres, openssl)
    .configure(config)
    .settings(
      nativeLinkStubs := false,
      libraryDependencies += "com.outr" %%% "scribe" % Versions.scribe,
      libraryDependencies += "com.lihaoyi" %%% "upickle" % Versions.upickle,
      libraryDependencies += "com.github.lolgab" %%% "snunit" % Versions.SNUnit,
      libraryDependencies += (
        "com.github.lolgab" %%% "snunit-routes" % Versions.SNUnit cross CrossVersion.for3Use2_13
      ).excludeAll(
        ExclusionRule("com.github.lolgab", "snunit_native0.4_2.13")
      )
    )

def config(p: Project) = {
  p.enablePlugins(ScalaNativePlugin, ScalaNativeJUnitPlugin)
    .settings(scalaVersion := Versions.Scala)
    .settings(nativeConfig ~= { conf =>
      conf
        .withLinkingOptions(
          conf.linkingOptions ++
            postgresLib.toList.map("-L" + _) ++
            opensslLib.toList.map("-L" + _) ++
            List("-lcrypto")
        )
        .withCompileOptions(
          conf.compileOptions ++
            List(opensslInclude).map("-I" + _)
        )
    })
    .settings(nativeConfig := {
      val conf = nativeConfig.value
      if (sys.env.get("SN_RELEASE").contains("fast"))
        conf.withOptimize(true).withLTO(LTO.thin).withMode(Mode.releaseFast)
      else conf
    })
}

lazy val postgres =
  project
    .in(file("postgres"))
    .enablePlugins(ScalaNativePlugin, BindgenPlugin)
    .settings(
      scalaVersion := Versions.Scala,
      resolvers += Resolver.sonatypeRepo("snapshots"),
      // Generate bindings to Postgres main API
      bindgenBindings +=
        Binding(
          postgresInclude.resolve("libpq-fe.h").toFile(),
          "libpq",
          linkName = Some("pq"),
          cImports = List("libpq-fe.h"),
          clangFlags = List(
            "-std=gnu99",
            s"-I$postgresInclude",
            "-fsigned-char"
          )
        ),
      nativeConfig ~= { conf =>
        conf.withLinkingOptions(
          conf.linkingOptions ++ postgresLib.toList.map("-L" + _)
        )
      }
    )

lazy val openssl =
  project
    .in(file("openssl"))
    .enablePlugins(ScalaNativePlugin, BindgenPlugin)
    .settings(
      scalaVersion := Versions.Scala,
      resolvers += Resolver.sonatypeRepo("snapshots"),
      // Generate bindings to Postgres main API
      bindgenBindings := {
        Seq(
          Binding(
            opensslHeader("openssl/sha.h").toFile(),
            "libcrypto",
            linkName = Some("crypto"),
            cImports = List("openssl/sha.h"),
            clangFlags = List(
              "-std=gnu99",
              s"-I$opensslInclude",
              "-fsigned-char"
            )
          ),
          Binding(
            opensslHeader("openssl/evp.h").toFile(),
            "libhmac",
            linkName = Some("crypto"),
            cImports = List("openssl/evp.h"),
            clangFlags = List(
              "-std=gnu99",
              s"-I$opensslInclude",
              "-fsigned-char"
            )
          )
        )
      },
      nativeConfig ~= { conf =>
        conf.withLinkingOptions(
          conf.linkingOptions ++ opensslLib.toList.map("-L" + _)
        )
      }
    )

def postgresInclude = {
  import Platform.*
  (os, arch) match {
    case (OS.Linux, _) => Paths.get("/usr/include/postgresql/")
    case (OS.MacOS, Arch.aarch64) =>
      Paths.get("/opt/homebrew/opt/libpq/include/")
    case (OS.MacOS, Arch.x86_64) => Paths.get("/usr/local/opt/libpq/include/")
  }
}

def postgresLib = {
  import Platform.*
  (os, arch) match {
    case (OS.MacOS, Arch.aarch64) =>
      Some(Paths.get("/opt/homebrew/opt/libpq/lib/"))
    case (OS.MacOS, Arch.x86_64) =>
      Some(Paths.get("/usr/local/opt/libpq/lib/"))
    case _ => None
  }
}

def opensslHeader(filename: String) = {
  opensslInclude.resolve(filename)
}

def opensslInclude = {
  import Platform.*
  (os, arch) match {
    case (OS.Linux, _) => Paths.get("/usr/include/")
    case (OS.MacOS, Arch.aarch64) =>
      Paths.get("/opt/homebrew/opt/openssl/include/")
  }
}

def opensslLib = {
  import Platform.*
  (os, arch) match {
    case (OS.MacOS, Arch.aarch64) =>
      Some(Paths.get("/opt/homebrew/opt/openssl/lib/"))
    case (OS.Linux, Arch.x86_64) =>
      Some(Paths.get("/lib/x86_64-linux-gnu/"))
    case _ => None
  }
}

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
  val f = new File("/opt/homebrew/var/run/unit/control.sock")

  if (f.exists()) {
    val cmd =
      "curl --unix-socket /opt/homebrew/var/run/unit/control.sock http://localhost/control/applications/app/restart"

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
