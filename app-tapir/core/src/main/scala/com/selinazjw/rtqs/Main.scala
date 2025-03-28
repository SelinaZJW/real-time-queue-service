package com.selinazjw.rtqs

import cats.effect.IO
import cats.syntax.all.*
import com.selinazjw.rtqs.routes.Routes
import com.selinazjw.rtqs.service.{QueueService, UserService, WorkerService}
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.Port

object Main {

  val routes =
    for {
      queueService <- QueueService.apply[IO]
      userService   = UserService.apply(queueService)
      workerService = WorkerService.apply(queueService)
      routes        = Routes.apply[IO](userService, workerService)
    } yield routes

  override def run: IO[Unit] = EmberServerBuilder
    .default[IO]
    .withPort(Port.fromInt(8080).getOrElse(throw new Exception("Port is not supported")))
    .withHttpWebSocketApp(wsb => routes.map(_.userServiceServerEndpoint))
}
