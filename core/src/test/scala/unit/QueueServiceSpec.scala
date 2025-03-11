package unit

import cats.effect.kernel.Resource
import cats.syntax.all.*
import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import cats.effect.std.Supervisor
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.unsafe.IORuntime
import fs2.concurrent.SignallingRef
import model.{UserPosition, UserSessionId}
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AsyncWordSpec}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import service.QueueService

import scala.concurrent.duration.DurationInt

// AsyncIOSpec works with AsyncWorkSpec but not AnyWordSpec ???
class QueueServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  final case class TestContext(
      userQueue: Queue[IO, UserPosition],
      assignedPositionCounter: Ref[IO, Int],
      latestServicedPositionSignal: SignallingRef[IO, Int],
      latestServicedPositionList: Ref[IO, List[Int]],
      queueService: QueueService[IO]
  )

  def withTestContext[A](test: TestContext => IO[A]): IO[A] =
    for {
      userQueue                    <- Queue.unbounded[IO, UserPosition]
      assignedPositionCounter      <- Ref.of[IO, Int](1)
      latestServicedPositionSignal <- SignallingRef[IO, Int](0)
      latestServicedPositionList   <- Ref[IO].of(List.empty[Int])
      queueService = new QueueService.QueueServiceInMemoryImpl[IO](userQueue,
                                                                   assignedPositionCounter,
                                                                   latestServicedPositionSignal)
      assertion <- test(
        TestContext(userQueue,
                    assignedPositionCounter,
                    latestServicedPositionSignal,
                    latestServicedPositionList,
                    queueService))
    } yield assertion

  "QueueService" when {
    ".addUser" should {
      "add userSessionId to the queue and update assigned position" in withTestContext {
        case TestContext(_, assignedPositionCounter, latestServicedPositionSignal, _, queueService) =>
          for {
            user1Position          <- queueService.addUser(UserSessionId("user1"))
            user2Position          <- queueService.addUser(UserSessionId("user2"))
            latestAssignedPosition <- assignedPositionCounter.get
            latestServedPosition   <- latestServicedPositionSignal.get
          } yield {
            user1Position shouldBe UserPosition(UserSessionId("user1"), 1)
            user2Position shouldBe UserPosition(UserSessionId("user2"), 2)
            latestAssignedPosition shouldBe 3
            latestServedPosition shouldBe 0
          }
      }
    }

    ".nextUser" should {
      "get next user from queue and update latest served position" in withTestContext {
        case TestContext(_, assignedPositionCounter, latestServicedPositionSignal, _, queueService) =>
          for {
            user1Position          <- queueService.addUser(UserSessionId("user1"))
            user2Position          <- queueService.addUser(UserSessionId("user2"))
            serveUser1             <- queueService.nextUser
            latestAssignedPosition <- assignedPositionCounter.get
            latestServedPosition   <- latestServicedPositionSignal.get
          } yield {
            serveUser1 shouldBe Some(UserPosition(UserSessionId("user1"), 1))
            latestAssignedPosition shouldBe 3
            latestServedPosition shouldBe 1
          }
      }

      "return None when queue is empty" in withTestContext {
        case TestContext(_, assignedPositionCounter, latestServicedPositionSignal, _, queueService) =>
          for {
            _                      <- queueService.addUser(UserSessionId("user1"))
            _                      <- queueService.addUser(UserSessionId("user2"))
            serveUser1             <- queueService.nextUser
            serveUser2             <- queueService.nextUser
            serveNext              <- queueService.nextUser
            latestAssignedPosition <- assignedPositionCounter.get
            latestServedPosition   <- latestServicedPositionSignal.get
          } yield {
            serveUser1 shouldBe Some(UserPosition(UserSessionId("user1"), 1))
            serveUser2 shouldBe Some(UserPosition(UserSessionId("user2"), 2))
            serveNext shouldBe None
            latestAssignedPosition shouldBe 3
            latestServedPosition shouldBe 2
          }
      }
    }

    ".subscribeToUpdates" should {
      "return a stream of latest service position updates until the user's assigned position" in withTestContext {
        case TestContext(_,
                         assignedPositionCounter,
                         latestServicedPositionSignal,
                         latestServicedPositionList,
                         queueService) =>
          for {
            user1Position <- queueService.addUser(UserSessionId("user1"))
            user2Position <- queueService.addUser(UserSessionId("user2"))

            // check out kafka-topic-loader tests for running streams in the background
            latestServedPositionStream = queueService
              .subscribeToUpdates(user2Position.assignedPosition)
              .evalTap(latestPosition =>
                IO.println(s"latest position: $latestPosition") *> latestServicedPositionList.update(
                  _ :+ latestPosition))
              .compile
              .toList
            latestServedPositionFiber <- latestServedPositionStream.start

            _          <- IO.sleep(3.seconds)
            serveUser1 <- queueService.nextUser
            _          <- IO.sleep(3.seconds)
            serveUser2 <- queueService.nextUser
            _          <- IO.sleep(3.seconds)
            serveNext  <- queueService.nextUser

            _ <- IO.sleep(2.seconds)
            _ <- IO.println("Done")

            servicedPositionStreamList <-latestServedPositionFiber.join.flatMap(outcome => outcome.embedError)
            latestServicedPositionList <- latestServicedPositionList.get
            latestAssignedPosition     <- assignedPositionCounter.get
            latestServicedPosition     <- latestServicedPositionSignal.get
          } yield {
            servicedPositionStreamList shouldBe List(0, 1, 2)
            latestServicedPositionList should contain theSameElementsAs List(0, 1, 2)
            latestAssignedPosition shouldBe 3
            latestServicedPosition shouldBe 2
          }

      }

      "return a stream of latest service position updates - using Resource.surround" in withTestContext {
        case TestContext(_,
                         assignedPositionCounter,
                         latestServicedPositionSignal,
                         latestServicedPositionList,
                         queueService) =>
          // Assert on the stream compile values directly by using Resource.use to access
          def streamResource(assignedUserPosition: Int): Resource[IO, IO[List[Int]]] = Supervisor[IO]
            .evalMap(_.supervise {
              queueService
                .subscribeToUpdates(assignedUserPosition)
                .evalTap(latestPosition =>
                  IO.println(s"latest position: $latestPosition") *> latestServicedPositionList.update(
                    _ :+ latestPosition))
                // .take(3) // need to complete the stream to call .join, but now taken care of .subscribeToUpdates
                .compile
                .toList
            })
            .map(fiber => fiber.join.flatMap(outcome => outcome.embedError))

          def startStreamingPositions(assignedUserPosition: Int): IO[List[Int]] =
            streamResource(assignedUserPosition).use { positions =>
              for {
                _          <- IO.sleep(3.seconds)
                serveUser1 <- queueService.nextUser
                _          <- IO.sleep(3.seconds)
                serveUser2 <- queueService.nextUser
                _          <- IO.sleep(3.seconds)
                serveNext  <- queueService.nextUser

                _                  <- IO.sleep(2.seconds)
                _                  <- IO.println("Done")
                _                  <- positions.map(success => println(s"Stream success: $success"))
                positionStreamList <- positions // better ways to access positions?
              } yield positionStreamList
            }

          for {
            user1Position <- queueService.addUser(UserSessionId("user1"))
            user2Position <- queueService.addUser(UserSessionId("user2"))

            servicedPositionStreamList <- startStreamingPositions(user2Position.assignedPosition)

            latestServicedPositionList <- latestServicedPositionList.get
            latestAssignedPosition     <- assignedPositionCounter.get
            latestServicedPosition     <- latestServicedPositionSignal.get
          } yield {
            servicedPositionStreamList shouldBe List(0, 1, 2)
            latestServicedPositionList should contain theSameElementsAs List(0, 1, 2)
            latestAssignedPosition shouldBe 3
            latestServicedPosition shouldBe 2
          }
      }

    }

  }
}
