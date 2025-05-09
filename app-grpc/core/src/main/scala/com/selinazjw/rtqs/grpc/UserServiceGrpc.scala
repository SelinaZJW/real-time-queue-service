package com.selinazjw.rtqs.grpc

import user as proto

import cats.effect.Async
import cats.effect.std.Console
import cats.syntax.all.*
import cats.{Monad, MonadThrow}
import UserServiceGrpc.validateRequest
import com.selinazjw.rtqs.model.UserSessionId
import com.selinazjw.rtqs.service.UserService
import fs2.Stream
import io.grpc.{Metadata, Status}

private final class UserServiceGrpc[F[_] : MonadThrow](userService: UserService[F])
    extends proto.RealTimeQueueUserServiceFs2Grpc[F, Metadata] {
  override def addUserAndSubscribe(request: proto.Request, ctx: Metadata): Stream[F, proto.PositionUpdate] =
    for {
      validatedRequest <- Stream.eval(validateRequest(request))
      positionUpdate         <- userService.addUserAndSubscribe(validatedRequest)
    } yield proto.PositionUpdate(positionUpdate.position)
}

object UserServiceGrpc {
  def validateRequest[F[_] : MonadThrow](request: proto.Request): F[UserSessionId] =
    request.userSessionId.id match {
      case "" =>
        MonadThrow[F].raiseError(
          Status.INVALID_ARGUMENT.withDescription("User session ID cannot be empty").asException())  // => effectful and contained in F
      // ??? benefits of this compared to:
      // throw new IllegalArgumentException("User session ID cannot be empty") => not effectful
      case id => UserSessionId(id).pure
    }

  def observed[F[_] : Console : MonadThrow, A](
      delegate: proto.RealTimeQueueUserServiceFs2Grpc[F, A]): proto.RealTimeQueueUserServiceFs2Grpc[F, A] =
    new proto.RealTimeQueueUserServiceFs2Grpc[F, A] {
      override def addUserAndSubscribe(request: proto.Request, ctx: A): Stream[F, proto.PositionUpdate] =
        for {
          _ <- Stream.eval(
            Console[F].println(s"Calling userService.addUserAndSubscribe for user ${request.userSessionId}"))
          response <- delegate
            .addUserAndSubscribe(request, ctx)
            .attempt
            .evalMap {
              case Left(throwable) =>
                Console[F].println(s"Error: ${throwable.getMessage}") *> MonadThrow[F].raiseError(throwable)
              case Right(position) =>
                Console[F].println(s"user ${request.userSessionId} current position: $position").as(position)
            }
        } yield response
    }

  def apply[F[_] : Async : Console](userService: UserService[F]): proto.RealTimeQueueUserServiceFs2Grpc[F, Metadata] =
    observed(new UserServiceGrpc(userService))
}
