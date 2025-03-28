package com.selinazjw.rtqs.service

import cats.MonadThrow
import cats.syntax.all.*
import com.selinazjw.rtqs.model.{ErrorResponse, InvalidArgument, PositionUpdate, UserSessionId}
import com.selinazjw.rtqs.service.UserServiceTapir.validateRequest
import fs2.Stream
import sttp.tapir.EndpointOutput.StatusCode
import sttp.tapir.static.StaticErrorOutput.BadRequest

final class UserServiceTapir[F[_] : MonadThrow](userService: UserService[F]) {
  def addUserAndSubscribe(userSessionId: UserSessionId): Stream[F, PositionUpdate] =
    for {
      validatedRequest <- Stream.eval(validateRequest(userSessionId))
      positionUpdates  <- userService.addUserAndSubscribe(validatedRequest)
    } yield positionUpdates

}

object UserServiceTapir {
  def validateRequest[F[_] : MonadThrow](userSessionId: UserSessionId): F[UserSessionId] =
    userSessionId.id match {
      case "" =>
        MonadThrow[F].raiseError(InvalidArgument("User session ID cannot be empty"))   // is this better as a decode error?
      case id => UserSessionId(id).pure
    }

  def apply[F[_]: MonadThrow](userService: UserService[F]): UserServiceTapir[F] = new UserServiceTapir[F](userService)
}
