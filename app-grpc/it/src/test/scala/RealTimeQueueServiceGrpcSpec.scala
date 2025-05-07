import RealTimeQueueServiceSpec.TestContext
import cats.effect.IO
import cats.syntax.all.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import cats.effect.testing.scalatest.AsyncIOSpec
import com.dimafeng.testcontainers.{ContainerDef, DockerComposeContainer, ExposedService}
import com.dimafeng.testcontainers.scalatest.{TestContainerForAll, TestContainerForEach}
import com.google.protobuf.empty.Empty
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import fs2.grpc.syntax.all.fs2GrpcSyntaxManagedChannelBuilder
import io.grpc.Metadata
import user.{PositionUpdate, RealTimeQueueUserServiceFs2Grpc, Request, UserSessionId}
import worker.{RealTimeQueueWorkerServiceFs2Grpc, UserPosition}

import java.io.File
import scala.concurrent.duration.DurationInt

class RealTimeQueueServiceGrpcSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with TestContainerForEach {

  override val containerDef: DockerComposeContainer.Def = DockerComposeContainer.Def(
    composeFiles = new File("app-grpc/it/src/test/resources/docker-compose.yaml"),
    exposedServices = Seq(ExposedService("real-time-queue-service-grpc", 8080))
  ) // specify container internal port, automatically maps to random host port

  def withTestContext[A](test: TestContext => IO[A]): IO[A] = withContainers { containers =>
    val appGrpcHost = containers.getServiceHost("real-time-queue-service-grpc", 8080)
    val appGrpcPort = containers.getServicePort("real-time-queue-service-grpc", 8080) // retrieve random port assigned

    val resources = for {
      channel <- NettyChannelBuilder
        .forAddress(appGrpcHost, appGrpcPort)
        .usePlaintext()
        .resource[IO]
      userServiceClient   <- RealTimeQueueUserServiceFs2Grpc.stubResource[IO](channel)
      workerServiceClient <- RealTimeQueueWorkerServiceFs2Grpc.stubResource[IO](channel)
    } yield (userServiceClient, workerServiceClient)

    resources.use((userServiceClient, workerServiceClient) => test(TestContext(userServiceClient, workerServiceClient)))
  }

  "RealTimeQueueService" should {
    "add users to queue, subscribe users to updates and serve next user to worker" in withTestContext {
      case TestContext(userServiceClient, workerServiceClient) =>
        for {
          user1PositionFiber <- userServiceClient
            .addUserAndSubscribe(Request(UserSessionId("user1")), Metadata())
            .compile
            .toList
            .start
          _ <- IO.sleep(2.seconds)
          user2PositionFiber <- userServiceClient
            .addUserAndSubscribe(Request(UserSessionId("user2")), Metadata())
            .compile
            .toList
            .start

          _              <- IO.sleep(2.seconds)
          user1Served    <- workerServiceClient.getNextUser(Empty.defaultInstance, Metadata())
          _              <- IO.sleep(2.seconds)
          user1Positions <- user1PositionFiber.join.flatMap(outcome => outcome.embedError)

          _ <- IO.sleep(2.seconds)
          user3PositionFiber <- userServiceClient
            .addUserAndSubscribe(Request(UserSessionId("user3")), Metadata())
            .compile
            .toList
            .start
          _ <- IO.sleep(2.seconds)
          user4PositionFiber <- userServiceClient
            .addUserAndSubscribe(Request(UserSessionId("user4")), Metadata())
            .compile
            .toList
            .start

          _              <- IO.sleep(2.seconds)
          user2Served    <- workerServiceClient.getNextUser(Empty.defaultInstance, Metadata())
          _              <- IO.sleep(2.seconds)
          user2Positions <- user2PositionFiber.join.flatMap(outcome => outcome.embedError)
          _              <- IO.sleep(2.seconds)
          user3Served    <- workerServiceClient.getNextUser(Empty.defaultInstance, Metadata())
          _              <- IO.sleep(2.seconds)
          user3Positions <- user3PositionFiber.join.flatMap(outcome => outcome.embedError)
          _              <- IO.sleep(2.seconds)
          user4Served    <- workerServiceClient.getNextUser(Empty.defaultInstance, Metadata())
          _              <- IO.sleep(2.seconds)
          user4Positions <- user4PositionFiber.join.flatMap(outcome => outcome.embedError)
        } yield {
          user1Served.userPosition shouldBe UserPosition(UserSessionId("user1"), 1).some
          user2Served.userPosition shouldBe UserPosition(UserSessionId("user2"), 2).some
          user3Served.userPosition shouldBe UserPosition(UserSessionId("user3"), 3).some
          user4Served.userPosition shouldBe UserPosition(UserSessionId("user4"), 4).some

          user1Positions.map(_.position) should contain theSameElementsInOrderAs List(1, 0)
          user2Positions.map(_.position) should contain theSameElementsInOrderAs List(2, 1, 0)
          user3Positions.map(_.position) should contain theSameElementsInOrderAs List(2, 1, 0)
          user4Positions.map(_.position) should contain theSameElementsInOrderAs List(3, 2, 1, 0)
        }
    }

    "fail request with Invalid Argument if user session ID is empty" in withTestContext {
      case TestContext(userServiceClient, _) =>
        userServiceClient
          .addUserAndSubscribe(Request(UserSessionId("")), Metadata())
          .compile
          .toList
          .assertThrowsWithMessage[io.grpc.StatusRuntimeException]("INVALID_ARGUMENT: User session ID cannot be empty")
    }
  }
}

object RealTimeQueueServiceSpec {
  final case class TestContext(
      userServiceClient: RealTimeQueueUserServiceFs2Grpc[IO, Metadata],
      workerServiceClient: RealTimeQueueWorkerServiceFs2Grpc[IO, Metadata]
  )
}
