package service

import cats.Monad
import cats.syntax.all.*
import cats.effect.{Concurrent, Ref}
import cats.effect.kernel.GenConcurrent
import cats.effect.std.Queue
import cats.instances.queue
import fs2.concurrent.{SignallingRef, Topic}
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
                                               assignedPositionCounter: Ref[F, Int],
                                               latestServicedPositionSignal: SignallingRef[F, Int])
      extends QueueService[F] {
    override def addUser(userSessionId: UserSessionId): F[UserPosition] =
      for {
        assignedPosition <- assignedPositionCounter.get
        userPosition = UserPosition(userSessionId, assignedPosition)
        _ <- userQueue.offer(userPosition)
        _ <- assignedPositionCounter.update(_ + 1)
      } yield userPosition

    override def nextUser: F[Option[UserPosition]] =
      for {
        userPosition <- userQueue.tryTake
        _ <- userPosition.fold(().pure) { nextUserPosition =>
          latestServicedPositionSignal.set(nextUserPosition.position)
        }
      } yield userPosition

    override def subscribeToUpdates: Stream[F, Int] =
      latestServicedPositionSignal.discrete
  }

  def apply[F[_] : Concurrent]: F[QueueService[F]] =
    for {
      userQueue                    <- Queue.unbounded[F, UserPosition]
      assignedPositionCounter      <- Ref.of[F, Int](1)
      latestServicedPositionSignal <- SignallingRef[F, Int](0)
    } yield new QueueServiceInMemoryImpl(userQueue, assignedPositionCounter, latestServicedPositionSignal)
}
