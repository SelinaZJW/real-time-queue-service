package grpc

import cats.{Applicative, Monad}
import cats.syntax.all.*
import com.google.protobuf.empty.Empty
import service.WorkerService
import worker as proto
import user as userProto

import cats.effect.std.Console

private final class WorkerServiceGrpc[F[_] : Monad](workerService: WorkerService[F])
    extends proto.RealTimeQueueWorkerServiceFs2Grpc[F, Unit] {
  override def getNextUser(request: Empty, ctx: Unit): F[proto.Response] =
    workerService.getNextUser.map { maybeNextUser =>
      proto.Response(maybeNextUser.map(nextUser =>
        proto.UserPosition(userProto.UserSessionId(nextUser.userSessionId.value), nextUser.assignedPosition)))
    }
}

object WorkerServiceGrpc {

  def observed[F[_] : Console : Monad, A](
      delegate: proto.RealTimeQueueWorkerServiceFs2Grpc[F, A]): proto.RealTimeQueueWorkerServiceFs2Grpc[F, A] =
    new proto.RealTimeQueueWorkerServiceFs2Grpc[F, A] {
      override def getNextUser(request: Empty, ctx: A): F[proto.Response] =
        for {
          _        <- Console[F].println("Calling workerUser.getNextUser")
          response <- delegate.getNextUser(request, ctx)
          _        <- Console[F].println(s"Serving $response")
        } yield response
    }

  def apply[F[_] : Monad : Console](workerService: WorkerService[F]): proto.RealTimeQueueWorkerServiceFs2Grpc[F, Unit] =
    observed(new WorkerServiceGrpc(workerService))
}
