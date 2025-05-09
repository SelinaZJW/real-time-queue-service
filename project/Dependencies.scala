import sbt.*

object Dependencies {

  // Versions
  lazy val catsCoreV                   = "2.12.0"
  lazy val catsEffectTestingScalatestV = "1.6.0"
  lazy val catsEffectV                 = "3.5.4"
  lazy val fs2CoreV                    = "3.11.0"
  lazy val log4CatsSlf4jV              = "2.7.0"
  lazy val pureconfigV                 = "0.17.7"
  lazy val scalatestV                  = "3.2.19"
  lazy val testcontainersV             = "0.43.0"
  lazy val tapirV                      = "1.11.20"
  lazy val sttpV                       = "4.0.0-RC2"
  lazy val http4sV                     = "0.23.30"

  // Dependencies

  // Testing
  lazy val catsEffectTestingScalatest = "org.typelevel" %% "cats-effect-testing-scalatest" % catsEffectTestingScalatestV
  lazy val scalaCheck     = "org.scalatestplus" %% "scalacheck-1-18"                % s"$scalatestV.0"
  lazy val scalatest      = "org.scalatest"     %% "scalatest"                      % scalatestV
  lazy val testcontainers = "com.dimafeng"      %% "testcontainers-scala-scalatest" % testcontainersV

  // Cats
  lazy val catsCore      = "org.typelevel" %% "cats-core"      % catsCoreV
  lazy val catsEffect    = "org.typelevel" %% "cats-effect"    % catsEffectV
  lazy val log4CatsSlf4j = "org.typelevel" %% "log4cats-slf4j" % log4CatsSlf4jV

  // Pureconfig
  lazy val pureconfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect"    % pureconfigV
  lazy val pureconfigCore       = "com.github.pureconfig" %% "pureconfig-core"           % pureconfigV
  lazy val pureconfigGeneric    = "com.github.pureconfig" %% "pureconfig-generic-scala3" % pureconfigV

  // Fs2
  lazy val fs2Core = "co.fs2" %% "fs2-core" % fs2CoreV

  // gRPC
  lazy val grpcNetty    = "io.grpc"               % "grpc-netty-shaded"    % scalapb.compiler.Version.grpcJavaVersion
  lazy val grpcServices = "io.grpc"               % "grpc-services"        % scalapb.compiler.Version.grpcJavaVersion
  lazy val scalaPB      = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion

  // Tapir
  lazy val tapir       = "com.softwaremill.sttp.tapir"   %% "tapir-core"          % tapirV
  lazy val tapirCirce  = "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"    % tapirV
  lazy val sttpFs2     = "com.softwaremill.sttp.client4" %% "fs2"                 % sttpV
  lazy val tapirHttp4s = "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server" % tapirV

  // http4s
  lazy val emberServer = "org.http4s" %% "http4s-ember-server" % http4sV
  lazy val emberClient = "org.http4s" %% "http4s-ember-client" % http4sV
  lazy val http4sCirce = "org.http4s" %% "http4s-circe"        % http4sV
}
