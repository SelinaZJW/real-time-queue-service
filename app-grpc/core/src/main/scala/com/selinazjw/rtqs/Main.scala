package com.selinazjw.rtqs

import cats.effect.{IO, IOApp}
import com.selinazjw.rtqs.grpc.RTQServiceDefinitions
import fs2.grpc.syntax.all.*
import io.grpc.ServerServiceDefinition
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService

import scala.jdk.CollectionConverters.*

object Main extends IOApp.Simple {
  val serverServiceDefinitions = RTQServiceDefinitions.apply[IO]

  def runServer(services: List[ServerServiceDefinition]) = NettyServerBuilder
    .forPort(8080)
    .addServices((services :+ ProtoReflectionService.newInstance().bindService()).asJava)
    .resource[IO]
    .evalMap(server => IO(server.start()))
    .useForever

  override def run: IO[Unit] = serverServiceDefinitions.use(runServer)
}
