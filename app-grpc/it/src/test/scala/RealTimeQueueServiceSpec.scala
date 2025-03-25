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

class RealTimeQueueServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with TestContainerForAll {

  override val containerDef: DockerComposeContainer.Def = DockerComposeContainer.Def(
    composeFiles = new File("app-grpc/it/src/test/resources/docker-compose.yaml"),
    exposedServices = Seq(ExposedService("real-time-queue-service-grpc", 8080)))  // specify contqiner internal port, maps to random host port

  def withTestContext[A](test: TestContext => IO[A]): IO[A] = withContainers { containers =>
    val appGrpcHost = containers.getServiceHost("app-grpc", 8080)
    val appGrpcPort = containers.getServicePort("app-grpc", 8080)   // retrieve random port assigned

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
          user2PositionFiber <- userServiceClient
            .addUserAndSubscribe(Request(UserSessionId("user2")), Metadata())
            .compile
            .toList
            .start

          user1Served    <- workerServiceClient.getNextUser(Empty.defaultInstance, Metadata())
          user1Positions <- user1PositionFiber.join.flatMap(outcome => outcome.embedError)

          user3PositionFiber <- userServiceClient
            .addUserAndSubscribe(Request(UserSessionId("user3")), Metadata())
            .compile
            .toList
            .start
          user4PositionFiber <- userServiceClient
            .addUserAndSubscribe(Request(UserSessionId("user4")), Metadata())
            .compile
            .toList
            .start

          user2Served    <- workerServiceClient.getNextUser(Empty.defaultInstance, Metadata())
          user2Positions <- user2PositionFiber.join.flatMap(outcome => outcome.embedError)
          user3Served    <- workerServiceClient.getNextUser(Empty.defaultInstance, Metadata())
          user3Positions <- user3PositionFiber.join.flatMap(outcome => outcome.embedError)
          user4Served    <- workerServiceClient.getNextUser(Empty.defaultInstance, Metadata())
          user4Positions <- user4PositionFiber.join.flatMap(outcome => outcome.embedError)
        } yield {
          user1Served shouldBe worker.Response(UserPosition(UserSessionId("user1"), 1).some)
          user2Served shouldBe worker.Response(UserPosition(UserSessionId("user2"), 2).some)
          user3Served shouldBe worker.Response(UserPosition(UserSessionId("user3"), 3).some)
          user4Served shouldBe worker.Response(UserPosition(UserSessionId("user4"), 4).some)

          user1Positions should contain theSameElementsInOrderAs List(PositionUpdate(1), PositionUpdate(0))
          user2Positions should contain theSameElementsInOrderAs List(PositionUpdate(2),
                                                                      PositionUpdate(1),
                                                                      PositionUpdate(0))
          user3Positions should contain theSameElementsInOrderAs List(PositionUpdate(3),
                                                                      PositionUpdate(2),
                                                                      PositionUpdate(1),
                                                                      PositionUpdate(0))
          user4Positions should contain theSameElementsInOrderAs List(PositionUpdate(4),
                                                                      PositionUpdate(3),
                                                                      PositionUpdate(2),
                                                                      PositionUpdate(1),
                                                                      PositionUpdate(0))
        }
    }
  }
}

object RealTimeQueueServiceSpec {
  final case class TestContext(
      userServiceClient: RealTimeQueueUserServiceFs2Grpc[IO, Metadata],
      workerServiceClient: RealTimeQueueWorkerServiceFs2Grpc[IO, Metadata]
  )
}
