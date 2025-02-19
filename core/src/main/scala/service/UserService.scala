package service

import cats.{Applicative, Monad}
import cats.effect.std.Queue
import cats.syntax.all.*
import model.{UserPosition, UserSessionId}
import fs2.Stream
import fs2.concurrent.Topic

trait UserService[F[_]] {
  def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, Int]
}

object UserService {

  class UserServiceImpl[F[_] : Applicative](queueService: QueueService[F]) extends UserService[F] {

    override def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, Int] = {
      val assignedUserPosition = queueService.addUser(userSessionId).map(_.position)
      queueService.subscribeToUpdates
        .evalMap(currentServedPosition => assignedUserPosition.map(_ - currentServedPosition))
        .takeWhile(_ > 0)    // terminate stream when user is served?
    }
  }
}
