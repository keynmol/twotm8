val BindgenVersion =
  sys.env.getOrElse("SN_BINDGEN_VERSION", "0.0.8")

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.indoorvivants" % "bindgen-sbt-plugin" % BindgenVersion)
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.4")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.10.1")
