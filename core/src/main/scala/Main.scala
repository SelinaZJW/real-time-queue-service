import cats.effect.{IO, IOApp, Sync}
import fs2.Stream
import model.{UserPosition, UserSessionId}
import service.{QueueService, UserService, WorkerService}

object Main extends IOApp.Simple {
  val program = for {
    queueService <- QueueService.apply[IO]
    userService   = UserService.apply[IO](queueService)
    workerService = WorkerService.apply[IO](queueService)

    // add user1, user2, user3 to queue, both get stream current position
    user1Stream = userService.addUserAndSubscribe(UserSessionId("user1"))
    user2Stream = userService.addUserAndSubscribe(UserSessionId("user2"))
    user3Stream = userService.addUserAndSubscribe(UserSessionId("user3"))

    serveUser1and2 = Stream.evalSeq(
      for {
        user1Served <- workerService.getNextUser.map(nextUser => nextUser.fold(0)(_))
        user2Served <- workerService.getNextUser.map(nextUser => nextUser.fold(0)(_))
      } yield Seq(user1Served, user2Served))

    _ <- Stream(user1Stream, user2Stream, user3Stream, serveUser1and2)
      .parJoin(4)
      .take(6)
      .compile
      .toList
      .map(streamPositions => println(s"user123 after 1 and 2 served: ${streamPositions}"))

    user4Stream = userService.addUserAndSubscribe(UserSessionId("user4"))

    serveUser3and4 = Stream.evalSeq(
      for {
        user1Served <- workerService.getNextUser.map(nextUser => nextUser.fold(0)(_))
        user2Served <- workerService.getNextUser.map(nextUser => nextUser.fold(0)(_))
      } yield Seq(user1Served, user2Served))

    _ <- Stream(user1Stream, user2Stream, user3Stream, user4Stream, serveUser1and2, serveUser3and4)
      .parJoin(6)
      .take(2)
      .compile
      .toList
      .map(streamPositions => println(s"user34 after 3 and 4 served: ${streamPositions}"))

    // serve user1, user1 stream terminates, user2, user3 get position update
    user1Served <- workerService.getNextUser

    // serve user1, user2 stream terminates, user3 get position update
    user2Served <- workerService.getNextUser

    // add user4 to queue, gets stream current position
    user4Stream = userService.addUserAndSubscribe(UserSessionId("user4"))

    // serve user3, user3 stream terminates, user4 get position update
    user3Served <- workerService.getNextUser
    // serve user4 stream terminates
    user4Served <- workerService.getNextUser

//    _ = user1Served.fold("empty")(p => s"served ${p.userSessionId}")
//    _ <- Stream(user1Stream, user2Stream, user3Stream, user4Stream).parJoin(4).compile.toList.map(println(_))

  } yield ()

  override def run: IO[Unit] = program
}
