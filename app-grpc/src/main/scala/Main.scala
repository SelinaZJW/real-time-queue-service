import cats.effect.{IO, IOApp}
import io.grpc.ServerServiceDefinition
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import fs2.grpc.syntax.all.*
import grpc.RTQServiceDefinitions

import scala.jdk.CollectionConverters.*

object Main extends IOApp.Simple {
  val serverServiceDefinitions = RTQServiceDefinitions.apply[IO]

  def runServer(services: List[ServerServiceDefinition]) = NettyServerBuilder
    .forPort(9999)
    .addServices(services.asJava)
    .resource[IO]
    .evalMap(server => IO(server.start()))
    .useForever

  override def run: IO[Unit] = serverServiceDefinitions.use(runServer)
}
