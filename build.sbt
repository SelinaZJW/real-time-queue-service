val scala3Version = "3.6.3"

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / run / fork := true
Global / test / fork := true

lazy val root = project
  .in(file("."))
  .settings(name := "real-time-queue-service")
  .aggregate(core, appGrpc, appGrpcIt, appTapir)

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
  .in(file("app-grpc/core"))
  .enablePlugins(Fs2Grpc, DockerPlugin, JavaAppPackaging)
  .dependsOn(core)
  .settings(
    name := "app-grpc",
    version := "0.1.0",
    scalaVersion := scala3Version,
    libraryDependencies ++=
      Seq(
        Dependencies.grpcNetty,
        Dependencies.scalaPB,
        Dependencies.grpcServices,
        "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
        "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0"
      ),
    Compile / mainClass := Some("com.selinazjw.rtqs.Main") // specify entry point main class
//    Compile / unmanagedSourceDirectories :=
//      (Compile / unmanagedSourceDirectories).value.filterNot(_.getName == "Client.scala")  // ignore Client app in build if it's not in src/main/scala
  )
  .settings(
    Docker / packageName := "real-time-queue-service/app-grpc", // image name
    Docker / dockerBaseImage := "openjdk:17-jdk-slim",          // base java app image
    Docker / dockerExposedPorts := Seq(8080),
    dockerUpdateLatest := true // always tagging image with "latest"
  )

lazy val appGrpcIt = project
  .in(file("app-grpc/it"))
  .dependsOn(appGrpc)
  .settings(
    name := "app-grpc-it",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++=
      Seq(
        Dependencies.testcontainers             % Test,
        Dependencies.scalatest                  % Test,
        Dependencies.catsEffectTestingScalatest % Test
      )
  )

lazy val appTapir = project
  .in(file("app-tapir/core"))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .dependsOn(core)
  .settings(
    name := "app-tapir",
    version := "0.1.0",
    scalaVersion := scala3Version,
    libraryDependencies ++=
      Seq(
        Dependencies.tapir,
        Dependencies.tapirCirce,
        Dependencies.sttpFs2,
        Dependencies.tapirHttp4s,
        Dependencies.emberServer,
        Dependencies.emberClient,
        Dependencies.http4sCirce
      )
  )
  .settings(
    Docker / packageName := "real-time-queue-service/tapir-grpc", // image name
    Docker / dockerBaseImage := "openjdk:17-jdk-slim",            // base java app image
    Docker / dockerExposedPorts := Seq(8080),
    dockerUpdateLatest := true // alwasy tagging image with "latest"
  )
