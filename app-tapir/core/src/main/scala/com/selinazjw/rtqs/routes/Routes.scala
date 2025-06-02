package com.selinazjw.rtqs.routes

import cats.effect.std.Console
import cats.effect.Async
import com.selinazjw.rtqs.service.{UserService, UserServiceTapir, WorkerService}
import cats.syntax.all.*
import com.selinazjw.rtqs.model.{ErrorResponse, InvalidArgument, NextUser, PositionUpdate, UserPosition, UserSessionId}
import com.selinazjw.rtqs.service.UserService
import io.circe.generic.auto.*
import io.circe.syntax.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import fs2.Stream
import fs2.Pipe
import sttp.capabilities
import sttp.model.{Method, StatusCode}
import sttp.model.headers.Origin
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}

final class Routes[F[_] : Async](userService: UserService[F], workerService: WorkerService[F]) {

  val corsInterceptor = CORSInterceptor.customOrThrow[F](
    CORSConfig.default
      .allowOrigin(Origin.Host("http", "localhost", 3000.some))
      .allowMethods(Method.GET)
      .allowHeaders("Content-Type", "Authorization")
      .allowCredentials
  )

  val customOptions = Http4sServerOptions.customiseInterceptors.prependInterceptor(corsInterceptor).options

  private def endpointToServerWebsocket(
      userSessionId: String): Either[(StatusCode, ErrorResponse), Pipe[F, Option[String], Option[PositionUpdate]]] =
    userSessionId match {
      case "" =>
        (StatusCode.BadRequest, ErrorResponse(400, "User session ID cannot be empty"))
          .asLeft[Pipe[F, Option[String], Option[PositionUpdate]]]
      case _ =>
        ((_: Stream[F, Option[String]]) =>
          userService.addUserAndSubscribe(UserSessionId(userSessionId)).map {
            case PositionUpdate(0) => None // emitting None does not closer frame for websocat??
            case positionUpdate    => positionUpdate.some
          })
          .asRight[(StatusCode, ErrorResponse)]
    }

  val userServiceEndpoint: Endpoint[Unit,
                                    String,
                                    (StatusCode, ErrorResponse),
                                    Pipe[F, Option[String], Option[PositionUpdate]],
                                    Fs2Streams[F] & capabilities.WebSockets] =
    endpoint.get // websocket generally uses get
      .in("add-user-and-subscribe")
      .in(
        query[String]("userSessionId")
      ) // unable to send UserSessionId as request jsonBody, send userSessionId as query param
      .out(
        webSocketBody[Option[String], CodecFormat.TextPlain, Option[PositionUpdate], CodecFormat.Json](Fs2Streams[F])
          .decodeCloseResponses(true)
      ) // stream entire messages ignoring input stream
      // .out(streamBody(Fs2Streams[F])(Schema.derived[PositionUpdate], CodecFormat.Json()))
      // only allow stream of bytes, also can't signal end of stream
      .errorOut(statusCode and jsonBody[ErrorResponse])

  val userServiceServerEndpoint: ServerEndpoint[Fs2Streams[F] & capabilities.WebSockets, F] =
    userServiceEndpoint.serverLogicPure(useSessionId => endpointToServerWebsocket(useSessionId))

  val workerServiceEndpoint = endpoint.get
    .in("get-next-user")
    .out(jsonBody[NextUser]) // error cases?

  val workerServiceServerEndpoint = workerServiceEndpoint.serverLogicSuccess(_ =>
    workerService.getNextUser.map(maybeNextUser => NextUser(maybeNextUser.nonEmpty, maybeNextUser)))

  val userServiceRoute = Http4sServerInterpreter[F](customOptions)
    .toWebSocketRoutes(userServiceServerEndpoint)

  val workerServiceRoute = Http4sServerInterpreter[F](customOptions)
    .toRoutes(workerServiceServerEndpoint)
}

object Routes {
  def apply[F[_] : Async](userService: UserService[F], workerService: WorkerService[F]): Routes[F] =
    new Routes[F](userService, workerService)
}
