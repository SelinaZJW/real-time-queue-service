package service

import cats.Monad
import cats.syntax.all.*
import fs2.concurrent.Topic
import model.UserPosition

trait WorkerService[F[_]] {
  def getNextUser: F[Option[UserPosition]]
//  def stopServing: F[Unit]   // stop serving, end all streams and clear memory
}

object WorkerService {

  class WorkerServiceImpl[F[_] : Monad](queueService: QueueService[F]) extends WorkerService[F] {
    override def getNextUser: F[Option[UserPosition]] = queueService.nextUser
  }

  def apply[F[_] : Monad](queueService: QueueService[F]): WorkerService[F] = new WorkerServiceImpl(queueService)
}
