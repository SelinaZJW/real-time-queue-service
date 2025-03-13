package grpc

import cats.Monad
import cats.syntax.all.*
import cats.effect.{Async, Concurrent}
import cats.effect.std.Console
import io.grpc.ServerServiceDefinition
import service.*
import user.RealTimeQueueUserServiceFs2Grpc
import worker.RealTimeQueueWorkerServiceFs2Grpc

object App {
  def apply[F[_]: Console: Async]: List[ServerServiceDefinition] =
    for {
      queueService <- QueueService.apply
      userService = UserService.apply(queueService)
      workerService = WorkerService.apply(queueService)
      userServiceGrpc = UserServiceGrpc.apply(userService)
      workerServiceGrpc = WorkerServiceGrpc.apply(workerService)
      userServiceDefinitions = RealTimeQueueUserServiceFs2Grpc.bindServiceResource[F](userServiceGrpc)
      workerserviceDefinitions = RealTimeQueueWorkerServiceFs2Grpc.bindServiceResource[F](workerServiceGrpc)
    } yield List(userServiceGrpc, workerServiceGrpc)
}
