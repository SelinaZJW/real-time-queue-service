package com.selinazjw.rtqs.service

import cats.MonadThrow
import cats.syntax.all.*
import com.selinazjw.rtqs.model.{ErrorResponse, InvalidArgument, PositionUpdate, UserSessionId}
import fs2.Stream
import sttp.tapir.EndpointOutput.StatusCode
import sttp.tapir.static.StaticErrorOutput.BadRequest

private final class UserServiceTapir[F[_]] {
  def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, PositionUpdate] = ???
}

object UserServiceTapir {
  def validateRequest[F[_] : MonadThrow](userSessionId: UserSessionId): F[UserSessionId] =
    userSessionId.id match {
      case "" =>
        MonadThrow[F].raiseError(InvalidArgument("User session ID cannot be empty"))
      case id => UserSessionId(id).pure
    }

  def apply[F[_]]: UserServiceTapir[F] = new UserServiceTapir[F]
}
