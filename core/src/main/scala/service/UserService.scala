package service

import cats.Applicative
import cats.effect.std.Queue
import cats.syntax.all.*
import model.{UserPosition, UserSessionId}
import fs2.Stream
import fs2.concurrent.Topic

trait UserService[F[_]] {
  def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, Int]
//  def subscribeToBroadcasts: Stream[F, BroadCastMessage]   // subscribe to broadcasts, e.g. day passes gone, all passes gone
}

object UserService {

  class UserServiceImpl[F[_] : Applicative](queueService: QueueService[F]) extends UserService[F] {

    override def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, Int] = {
      val assignedUserPosition = queueService.addUser(userSessionId).map(_.position)
      queueService.subscribeToUpdates
        .evalMap(currentServedPosition => assignedUserPosition.map(assigned => assigned - currentServedPosition))
        .takeWhile(_ > 0) // terminate stream when user is served?
    }
  }

  def apply[F[_] : Applicative](queueService: QueueService[F]): UserService[F] = new UserServiceImpl(queueService)
}
