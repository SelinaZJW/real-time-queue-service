val scala3Version = "3.6.3"

lazy val root = project
  .in(file("."))
  .settings(name := "real-time-queue-service")
  .aggregate(core, appGrpc)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "core",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++=
      Seq(
        Dependencies.catsCore,
        Dependencies.fs2Core,
        Dependencies.scalatest                  % Test,
        Dependencies.scalaCheck                 % Test,
        Dependencies.catsEffectTestingScalatest % Test,
        "org.scalameta"                        %% "munit" % "1.0.0" % Test
      )
  )

lazy val appGrpc = project
  .in(file("app-grpc"))
  .enablePlugins(Fs2Grpc)
  .dependsOn(core)
  .settings(
    name := "app-grpc",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++=
      Seq(
        Dependencies.grpcNetty,
        Dependencies.scalaPB,
        "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
        "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0"
      )
  )
