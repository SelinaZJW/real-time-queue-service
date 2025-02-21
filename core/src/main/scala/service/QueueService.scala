package service

import cats.Monad
import cats.syntax.all.*
import cats.effect.{Concurrent, Ref}
import cats.effect.kernel.GenConcurrent
import cats.effect.std.{Console, Queue}
import cats.instances.queue
import fs2.concurrent.{SignallingRef, Topic}
import fs2.Stream
import model.*

trait QueueService[F[_]] {
  def addUser(userSessionId: UserSessionId): F[UserPosition]
  def nextUser: F[Option[UserPosition]]
  def subscribeToUpdates: Stream[F, Int] // can terminate this with an allSoldOut exception
//  def removeUser(userSessionId: UserSessionId): F[Unit]
//  def getCurrentPosition(userSessionId: UserSessionId): F[Option[Int]]
}

object QueueService {

  // add logging in observed
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

  // add println here
  def observed[F[_] : Console : Monad](delegate: QueueService[F]): QueueService[F] = new QueueService[F]:
    override def addUser(userSessionId: UserSessionId): F[UserPosition] =
      for {
        _            <- Console[F].println(s"Calling .addUser $userSessionId")
        userPosition <- delegate.addUser(userSessionId)
        _            <- Console[F].println(s"Added $userPosition")
      } yield userPosition

    override def nextUser: F[Option[UserPosition]] = delegate.nextUser

    override def subscribeToUpdates: Stream[F, Int] = delegate.subscribeToUpdates

  def apply[F[_] : Concurrent: Console]: F[QueueService[F]] =
    for {
      userQueue                    <- Queue.unbounded[F, UserPosition]
      assignedPositionCounter      <- Ref.of[F, Int](1)
      latestServicedPositionSignal <- SignallingRef[F, Int](0)
    } yield observed(new QueueServiceInMemoryImpl(userQueue, assignedPositionCounter, latestServicedPositionSignal))
}
