package service

import cats.Monad
import cats.effect.std.Queue
import cats.syntax.all.*
import model.{UserPosition, UserSessionId}
import fs2.Stream

trait UserService[F[_]] {
  def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, Int]
}

object UserService {
  class UserServiceImpl[F[_] : Monad](queueService: QueueService[F], userQueue: Queue[F, UserPosition])
      extends UserService[F] {
    override def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, Int] = {
      val userPosition = queueService.addUser(userSessionId)
      for {
        userPosition <- queueService.addUser(userSessionId)
        currentPosition              = userPosition.position
        currentPositionStreamMessage = Stream.emit(currentPosition)
        updatedPositionStreamMessage = Stream
          .fromQueueUnterminated(userQueue, currentPosition)
          .map(currentPosition - _.position)
      } yield currentPositionStreamMessage ++ updatedPositionStreamMessage
    }
  }
}
