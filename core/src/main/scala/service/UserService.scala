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
      val userPosition = queueService.addUser(userSessionId).map(_.position)
      // first stream message is user position not deducted by currentServedPosition
      // updates are real position in queue
      Stream.eval(userPosition) ++ queueService.subscribeToUpdates.evalMap(currentServedPosition =>
        userPosition.map(_ - currentServedPosition))
    }
  }
}
