package service

import cats.Monad
import cats.syntax.all.*
import fs2.concurrent.Topic
import model.UserPosition

trait WorkerService[F[_]] {
  def getNextUser: F[Option[UserPosition]]
}

object WorkerService {
  class WorkerServiceImpl[F[_] : Monad](queueService: QueueService[F]) extends WorkerService[F] {
    override def getNextUser: F[Option[UserPosition]] = queueService.nextUser
  }
}
