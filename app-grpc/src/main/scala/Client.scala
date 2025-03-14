import cats.effect.{IO, IOApp}
import cats.effect.kernel.Resource
import io.grpc.{InsecureChannelCredentials, ManagedChannel, Metadata}
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import fs2.grpc.syntax.all.*
import user.{RealTimeQueueUserServiceFs2Grpc, Request, UserSessionId}
import worker.RealTimeQueueWorkerServiceFs2Grpc
import com.google.protobuf.empty.Empty

import scala.concurrent.duration.DurationInt

object Client extends IOApp.Simple {
  val managedChannelResource: Resource[IO, ManagedChannel] =
    NettyChannelBuilder
      .forAddress("localhost", 8080, InsecureChannelCredentials.create())
      .resource[IO]

  def program(userServiceStub: RealTimeQueueUserServiceFs2Grpc[IO, Metadata],
              workerServiceStub: RealTimeQueueWorkerServiceFs2Grpc[IO, Metadata]) =
    for {
      _ <- IO.println("Begin program")

      _           <- IO.sleep(2.seconds)
      _ <- userServiceStub.addUserAndSubscribe(Request(UserSessionId("user1")), Metadata()).compile.drain.start
      _           <- IO.sleep(2.seconds)
      _ <- userServiceStub.addUserAndSubscribe(Request(UserSessionId("user2")), Metadata()).compile.drain.start

      _           <- IO.sleep(2.seconds)
      _ <- workerServiceStub.getNextUser(Empty(), Metadata())

      _           <- IO.sleep(2.seconds)
      _ <- userServiceStub.addUserAndSubscribe(Request(UserSessionId("user3")), Metadata()).compile.drain.start
      _           <- IO.sleep(2.seconds)
      _ <- userServiceStub.addUserAndSubscribe(Request(UserSessionId("user4")), Metadata()).compile.drain.start

      _           <- IO.sleep(2.seconds)
      _ <- workerServiceStub.getNextUser(Empty(), Metadata())
      _ <- workerServiceStub.getNextUser(Empty(), Metadata())
      _ <- workerServiceStub.getNextUser(Empty(), Metadata())
    } yield ()

  override def run: IO[Unit] = managedChannelResource.flatMap { channel =>
    for {
      userServiceStub   <- RealTimeQueueUserServiceFs2Grpc.stubResource[IO](channel)
      workerServiceStub <- RealTimeQueueWorkerServiceFs2Grpc.stubResource[IO](channel)
    } yield (userServiceStub, workerServiceStub)
  }.use((userServiceStub, workerServiceStub) => program(userServiceStub, workerServiceStub))
}
