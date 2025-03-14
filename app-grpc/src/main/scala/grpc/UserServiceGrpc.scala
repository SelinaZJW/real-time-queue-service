package grpc

import cats.{Monad, MonadThrow}
import cats.syntax.all.*
import cats.effect.std.Console
import fs2.Stream
import grpc.UserServiceGrpc.validateRequest
import service.UserService
import model.UserSessionId
import user as proto

import cats.effect.Async
import io.grpc.{Metadata, Status}

private final class UserServiceGrpc[F[_] : MonadThrow](userService: UserService[F])
    extends proto.RealTimeQueueUserServiceFs2Grpc[F, Metadata] {
  override def addUserAndSubscribe(request: proto.Request, ctx: Metadata): Stream[F, proto.PositionUpdate] =
    for {
      validatedRequest <- Stream.eval(validateRequest(request))
      position         <- userService.addUserAndSubscribe(validatedRequest)
    } yield proto.PositionUpdate(position)
}

object UserServiceGrpc {
  def validateRequest[F[_] : MonadThrow](request: proto.Request): F[UserSessionId] =
    request.userSessionId.id match {
      case "" =>
        MonadThrow[F].raiseError(
          Status.INVALID_ARGUMENT.withDescription("User session ID cannot be empty").asException())
      // ??? benefits of this compared to:
      // throw new IllegalArgumentException("User session ID cannot be empty")
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
