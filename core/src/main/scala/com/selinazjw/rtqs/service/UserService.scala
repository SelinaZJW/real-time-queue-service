package com.selinazjw.rtqs.service

import cats.effect.std.Queue
import cats.syntax.all.*
import cats.{Applicative, Monad}
import com.selinazjw.rtqs.model.{PositionUpdate, UserSessionId}
import fs2.Stream
import fs2.concurrent.Topic

trait UserService[F[_]] {
  def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, PositionUpdate]
//  def subscribeToBroadcasts: Stream[F, BroadCastMessage]   // subscribe to broadcasts, e.g. day passes gone, all passes gone
}

// add testing for this with stubbed queueService
object UserService {

  class UserServiceImpl[F[_] : Monad](queueService: QueueService[F]) extends UserService[F] {

    override def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, PositionUpdate] =
      for {
        assignedUserPosition <- Stream.eval(
          queueService.addUser(userSessionId).map(_.assignedPosition)
        ) // only evaluate this once
        updates <- queueService
          .subscribeToUpdates(assignedUserPosition)
          .map(currentServedPosition => assignedUserPosition - currentServedPosition)
//          .takeWhile(_ > 0)
      } yield PositionUpdate(updates)
  }

  def apply[F[_] : Monad](queueService: QueueService[F]): UserService[F] = new UserServiceImpl(queueService)
}
