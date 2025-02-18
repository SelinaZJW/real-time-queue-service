package service

import cats.Monad
import cats.syntax.all.*
import cats.effect.Ref
import cats.effect.std.Queue
import cats.instances.queue
import fs2.concurrent.Topic
import fs2.Stream
import model.*

trait QueueService[F[_]] {
  def addUser(userSessionId: UserSessionId): F[UserPosition]
  def nextUser: F[Option[UserPosition]]
  def subscribeToUpdates: Stream[F, Int]
//  def removeUser(userSessionId: UserSessionId): F[Unit]
//  def getCurrentPosition(userSessionId: UserSessionId): F[Option[Int]]
}

object QueueService {
  class QueueServiceInMemoryImpl[F[_] : Monad](userQueue: Queue[F, UserPosition],
                                               positionCounter: Ref[F, Int],
                                               servicedUserTopic: Topic[F, UserPosition])
      extends QueueService[F] {
    override def addUser(userSessionId: UserSessionId): F[UserPosition] =
      for {
        position <- positionCounter.get
        userPosition = UserPosition(userSessionId, position)
        _ <- userQueue.offer(userPosition)
        _ <- positionCounter.update(_ + 1)
      } yield userPosition

    override def nextUser: F[Option[UserPosition]] =
      for {
        userPosition <- userQueue.tryTake
        _ <- userPosition.fold(().pure) { nextUserPosition =>
          servicedUserTopic.publish1(nextUserPosition) *> positionCounter.update(_ - 1)
        }
      } yield userPosition

    override def subscribeToUpdates: Stream[F, Int] = servicedUserTopic.subscribeUnbounded.map(_.position)
  }
}
