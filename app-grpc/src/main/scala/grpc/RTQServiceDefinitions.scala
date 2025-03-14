package grpc

import cats.Monad
import cats.effect.kernel.Resource
import cats.effect.syntax.all.*
import cats.effect.{Async, Concurrent}
import cats.effect.std.Console
import io.grpc.ServerServiceDefinition
import service.*
import user.RealTimeQueueUserServiceFs2Grpc
import worker.RealTimeQueueWorkerServiceFs2Grpc

object RTQServiceDefinitions {
  def apply[F[_] : Console : Async]: Resource[F, List[ServerServiceDefinition]] =
    for {
      queueService <- QueueService.apply.toResource
      userService       = UserService.apply(queueService)
      workerService     = WorkerService.apply(queueService)
      userServiceGrpc   = UserServiceGrpc.apply(userService)
      workerServiceGrpc = WorkerServiceGrpc.apply(workerService)
      userServiceDefinition   <- RealTimeQueueUserServiceFs2Grpc.bindServiceResource[F](userServiceGrpc)
      workerServiceDefinition <- RealTimeQueueWorkerServiceFs2Grpc.bindServiceResource[F](workerServiceGrpc)
    } yield List(userServiceDefinition, workerServiceDefinition)
}
