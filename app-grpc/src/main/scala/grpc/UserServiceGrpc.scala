package grpc

import cats.Monad
import cats.syntax.all.*
import cats.effect.std.Console
import fs2.Stream
import grpc.UserServiceGrpc.validateRequest
import service.UserService
import model.UserSessionId
import user as proto

import cats.effect.Async
import io.grpc.Metadata

private final class UserServiceGrpc[F[_]](userService: UserService[F])
    extends proto.RealTimeQueueUserServiceFs2Grpc[F, Metadata] {
  override def addUserAndSubscribe(request: proto.Request, ctx: Metadata): Stream[F, proto.PositionUpdate] =
    userService.addUserAndSubscribe(validateRequest(request)).map(position => proto.PositionUpdate(position))
}

object UserServiceGrpc {
  def validateRequest(request: proto.Request): UserSessionId =
    request.userSessionId.id match {
      case "" => throw new IllegalArgumentException("User session ID cannot be empty")
      case id => UserSessionId(id)
    }

  def observed[F[_] : Console : Monad, A](
      delegate: proto.RealTimeQueueUserServiceFs2Grpc[F, A]): proto.RealTimeQueueUserServiceFs2Grpc[F, A] =
    new proto.RealTimeQueueUserServiceFs2Grpc[F, A] {
      override def addUserAndSubscribe(request: proto.Request, ctx: A): Stream[F, proto.PositionUpdate] =
        for {
          _ <- Stream.eval(
            Console[F].println(s"Calling userService.addUserAndSubscribe for user ${request.userSessionId}"))
          response <- delegate
            .addUserAndSubscribe(request, ctx)
            .evalTap(position => Console[F].println(s"user ${request.userSessionId} current position: $position"))
        } yield response
    }

  def apply[F[_] : Async : Console](userService: UserService[F]): proto.RealTimeQueueUserServiceFs2Grpc[F, Metadata] =
    observed(new UserServiceGrpc(userService))
}
