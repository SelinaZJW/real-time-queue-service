package unit

import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.unsafe.IORuntime
import fs2.concurrent.SignallingRef
import model.{UserPosition, UserSessionId}
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

  def withTestContext[A](test: TestContext => IO[A]): IO[A] = {
    val userQueue                    = Queue.unbounded[IO, UserPosition].unsafeRunSync()
    val assignedPositionCounter      = Ref.of[IO, Int](1).unsafeRunSync()
    val latestServicedPositionSignal = SignallingRef[IO, Int](0).unsafeRunSync()
    val latestServicedPositionList   = Ref[IO].of(List.empty[Int]).unsafeRunSync()

    val queueService =
      new QueueService.QueueServiceInMemoryImpl[IO](userQueue, assignedPositionCounter, latestServicedPositionSignal)

    test(
      TestContext(userQueue,
                  assignedPositionCounter,
                  latestServicedPositionSignal,
                  latestServicedPositionList,
                  queueService))
  }

  "QueueService" when {
    ".addUser" should {
      "add userSessionId to the queue and update assigned position" in withTestContext {
        case TestContext(_, assignedPositionCounter, latestServicedPositionSignal, _, queueService) =>
          val result = for {
            user1Position <- queueService
              .addUser(UserSessionId("user1"))
            user2Position <- queueService
              .addUser(UserSessionId("user2"))
            latestAssignedPosition <- assignedPositionCounter.get
            latestServedPosition   <- latestServicedPositionSignal.get
          } yield (user1Position, user2Position, latestAssignedPosition, latestServedPosition)

          result.asserting {
            case (user1Position, user2Position, latestAssignedPosition, latestServedPosition) =>
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
          val result = for {
            user1Position          <- queueService.addUser(UserSessionId("user1"))
            user2Position          <- queueService.addUser(UserSessionId("user2"))
            serveUser1             <- queueService.nextUser
            latestAssignedPosition <- assignedPositionCounter.get
            latestServedPosition   <- latestServicedPositionSignal.get
          } yield (user1Position, user2Position, serveUser1, latestAssignedPosition, latestServedPosition)

          result.asserting {
            case (user1Position, user2Position, serveUser1, latestAssignedPosition, latestServedPosition) =>
              serveUser1 shouldBe Some(UserPosition(UserSessionId("user1"), 1))
              latestAssignedPosition shouldBe 3
              latestServedPosition shouldBe 1
          }

      }

      "return None when queue is empty" in withTestContext {
        case TestContext(_, assignedPositionCounter, latestServicedPositionSignal, _, queueService) =>
          val result = for {
            _                      <- queueService.addUser(UserSessionId("user1"))
            _                      <- queueService.addUser(UserSessionId("user2"))
            serveUser1             <- queueService.nextUser
            serveUser2             <- queueService.nextUser
            serveNext              <- queueService.nextUser
            latestAssignedPosition <- assignedPositionCounter.get
            latestServedPosition   <- latestServicedPositionSignal.get
          } yield (serveUser1, serveUser2, serveNext, latestAssignedPosition, latestServedPosition)

          result.asserting {
            case (serveUser1, serveUser2, serveNext, assignedPositionCounter, latestServicedPositionSignal) =>
              serveUser1 shouldBe Some(UserPosition(UserSessionId("user1"), 1))
              serveUser2 shouldBe Some(UserPosition(UserSessionId("user2"), 2))
              serveNext shouldBe None
              assignedPositionCounter shouldBe 3
              latestServicedPositionSignal shouldBe 2
          }
      }
    }

//    ".subscribeToUpdates" should {
//      "return a stream of latest service position updates - working" in withTestContext {
//        case TestContext(_, assignedPositionCounter, latestServicedPositionSignal, latestServicedPositionList, queueService) =>{
//
//          val run = for {
//            user1Position <- queueService.addUser(UserSessionId("user1"))
//            user2Position <- queueService.addUser(UserSessionId("user2"))
//
//            // check out kafka-topic-loader tests for running streams in the background
//            latestServedPositionStream = queueService.subscribeToUpdates
//              .evalTap(latestPosition =>
//                IO.println(s"latest position: $latestPosition") *> latestServicedPositionList.update(
//                  _ :+ latestPosition))
//              .take(1)
//              .compile
//              .toList
//            latestServedPositionFiber <- latestServedPositionStream.start
//
//            _          <- IO.sleep(2.seconds)
//            serveUser1 <- queueService.nextUser
//            _          <- IO.sleep(2.seconds)
//            serveUser2 <- queueService.nextUser
//            _          <- IO.sleep(2.seconds)
//            serveNext  <- queueService.nextUser
//
//            _ <- IO.sleep(2.seconds)
//            _ <- latestServedPositionFiber.cancel
//            messages = latestServedPositionStream.unsafeRunSync()
//            _        = println(messages)
//            // _ <- latestServedPositionStream.map(println(_))
//            _ <- IO.println("Done")
//
//          } yield ()
//
//          run.unsafeRunSync()
//          latestServicedPositionList.get.unsafeRunSync() should contain theSameElementsAs List(0, 1, 2)
//          assignedPositionCounter.get.unsafeRunSync() shouldBe 3
//          latestServicedPositionSignal.get.unsafeRunSync() shouldBe 2
//        }
//      }
//
//      "return a stream of latest service position updates" in {
//        new TestContext {
//          val user1Position = queueService.addUser(UserSessionId("user1")).unsafeRunSync()
//          val user2Position = queueService.addUser(UserSessionId("user2")).unsafeRunSync()
//
//          queueService.subscribeToUpdates
//            .evalTap(latestPosition => IO.println(s"latest position: $latestPosition"))
//            .compile
//            .drain
//            .start
//            .unsafeRunSync()
//
//          IO.sleep(2.seconds)
//          val serveUser1 = queueService.nextUser.unsafeRunSync()
//          IO.sleep(2.seconds)
//          val serveUser2 = queueService.nextUser.unsafeRunSync()
//          IO.sleep(2.seconds)
//          val serveNext = queueService.nextUser.unsafeRunSync()
//
//          // ?? why only 1
//          // val latestServicedPositionStream = queueService.subscribeToUpdates.take(1).compile.toList.unsafeRunSync()
//
//          // println(latestServicedPositionStream)
//          // queueService.subscribeToUpdates.take(2).compile.toList.unsafeRunSync() shouldBe List(1, 2)
//          assignedPositionCounter.get.unsafeRunSync() shouldBe 3
//          latestServicedPositionSignal.get.unsafeRunSync() shouldBe 2
//        }
//      }
//    }

  }
}
