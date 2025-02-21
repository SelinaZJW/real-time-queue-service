package service

import cats.{Applicative, Monad}
import cats.effect.std.Queue
import cats.syntax.all.*
import model.{UserPosition, UserSessionId}
import fs2.Stream
import fs2.concurrent.Topic

trait UserService[F[_]] {
  def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, Int]
//  def subscribeToBroadcasts: Stream[F, BroadCastMessage]   // subscribe to broadcasts, e.g. day passes gone, all passes gone
}

// add testing for this with stubbed queueService
object UserService {

  class UserServiceImpl[F[_] : Monad](queueService: QueueService[F]) extends UserService[F] {

    override def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, Int] =
//      val assignedUserPosition = queueService.addUser(userSessionId).map(_.position)
//      //Stream.eval(assignedUserPosition).
//      queueService.subscribeToUpdates
//        .evalMap(currentServedPosition =>
//              assignedUserPosition.map(assigned => assigned - currentServedPosition))  // can't do this bc it will evaluate assignedUserPositioin everytime there's an update
//        .takeWhile(_ > 0)

      for {
        assignedUserPosition <- Stream.eval(
          queueService.addUser(userSessionId).map(_.position)
        ) // only evaluate this once
        updates <- queueService.subscribeToUpdates
          .map(currentServedPosition => assignedUserPosition - currentServedPosition)
          .takeWhile(_ > 0)
      } yield updates
  }

  def apply[F[_] : Monad](queueService: QueueService[F]): UserService[F] = new UserServiceImpl(queueService)
}
