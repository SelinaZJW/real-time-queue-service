package com.selinazjw.rtqs

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import com.selinazjw.rtqs.routes.Routes
import com.selinazjw.rtqs.service.{QueueService, UserService, WorkerService}
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.Port
import com.comcast.ip4s.*
import org.http4s.server.Router

object Main extends IOApp.Simple {

  val routes =
    for {
      queueService <- QueueService.apply[IO]
      userService   = UserService.apply(queueService)
      workerService = WorkerService.apply(queueService)
      routes        = Routes.apply[IO](userService, workerService)
    } yield routes

  override def run: IO[Unit] = routes.flatMap { routes =>
    EmberServerBuilder
      .default[IO]
      .withPort(port"8080")
      .withHttpWebSocketApp(wsb =>
        Router("/real-time-queue-service/user"   -> routes.userServiceRoute(wsb),
               "/real-time-queue-service/worker" -> routes.workerServiceRoute).orNotFound)   // combine both routes, otherwise default route to http
//      .withHttpApp(Router("/real-time-queue-service/worker" -> routes.workerServiceRoute).orNotFound)
      .build
      .useForever
  }
}
