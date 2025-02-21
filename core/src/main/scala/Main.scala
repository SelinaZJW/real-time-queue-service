import cats.effect.{IO, IOApp, Sync}
import fs2.Stream
import model.{UserPosition, UserSessionId}
import service.{QueueService, UserService, WorkerService}

import scala.concurrent.duration.DurationInt

object Main extends IOApp.Simple {
  val program = for {
    queueService <- QueueService.apply[IO]
    userService   = UserService.apply[IO](queueService)
    workerService = WorkerService.apply[IO](queueService)

    // add user1, user2, user3 to queue, both get stream current position
    _           <- IO.sleep(2.seconds)
    user1Stream <- userService
      .addUserAndSubscribe(UserSessionId("user1"))
      .evalTap(latestPosition => IO.println(s"user1: $latestPosition"))
      .compile
      .toList
      .start
    _           <- IO.sleep(2.seconds)
    user2Stream <- userService
      .addUserAndSubscribe(UserSessionId("user2"))
      .evalTap(latestPosition => IO.println(s"user2: $latestPosition"))
      .compile
      .toList
      .start
    _           <- IO.sleep(2.seconds)
    user3Stream <- userService
      .addUserAndSubscribe(UserSessionId("user3"))
      .evalTap(latestPosition => IO.println(s"user3: $latestPosition"))
      .compile
      .toList
      .start

    // serve user1 and user2, stream terminates, user2, user3 get position update
    _           <- IO.sleep(2.seconds)
    user1Served <- workerService.getNextUser.map(nextUser => nextUser.fold(0)(_))
    _           <- IO.sleep(2.seconds)
    user2Served <- workerService.getNextUser.map(nextUser => nextUser.fold(0)(_))

    // add user4 to queue, both get stream current position
    _           <- IO.sleep(2.seconds)
    user4Stream <- userService
      .addUserAndSubscribe(UserSessionId("user4"))
      .evalTap(latestPosition => IO.println(s"user4: $latestPosition"))
      .compile
      .toList
      .start

    // serve user3 and user4, stream terminates
    _           <- IO.sleep(2.seconds)
    user3Served <- workerService.getNextUser.map(nextUser => nextUser.fold(0)(_))
    _           <- IO.sleep(2.seconds)
    user4Served <- workerService.getNextUser.map(nextUser => nextUser.fold(0)(_))

  } yield ()

  override def run: IO[Unit] = program
}
