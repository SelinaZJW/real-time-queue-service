package com.selinazjw.rtqs.routes

import cats.effect.std.Console
import cats.effect.Async
import com.selinazjw.rtqs.service.{UserService, UserServiceTapir, WorkerService}
import cats.syntax.all.*
import com.selinazjw.rtqs.model.{ErrorResponse, InvalidArgument, PositionUpdate, UserPosition, UserSessionId}
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
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.http4s.Http4sServerInterpreter

final class Routes[F[_] : Async](userService: UserService[F], workerService: WorkerService[F]) {

//  private def endpointToServer(userSessionId: UserSessionId) =
//    userServiceTapir
//      .addUserAndSubscribe(userSessionId)
//      .pure
//      .map(_.flatMap(positionUpdate =>
//        Stream.emits(positionUpdate.asJson.noSpaces.getBytes)) // serialise PositionUpdate Stream to Byte Stream
//        .asRight[(StatusCode, ErrorResponse)])
//      .recoverWith {
//        case InvalidArgument(message) =>
//          (StatusCode(400), ErrorResponse(400, message)).asLeft[Pipe[F, String, PositionUpdate]].pure
//      }

  private def endpointToServerWebsocket(
      userSessionId: UserSessionId): Either[(StatusCode, ErrorResponse), Pipe[F, String, PositionUpdate]] =
    userSessionId.id match {
      case "" =>
        (StatusCode.BadRequest, ErrorResponse(400, "User session ID cannot be empty"))
          .asLeft[Pipe[F, String, PositionUpdate]]
      case _ =>
        ((_: Stream[F, String]) => userService.addUserAndSubscribe(userSessionId))
          .asRight[(StatusCode, ErrorResponse)]
    }

  val userServiceEndpoint: Endpoint[Unit,
                                    UserSessionId,
                                    (StatusCode, ErrorResponse),
                                    Pipe[F, String, PositionUpdate],
                                    Fs2Streams[F] & capabilities.WebSockets] = endpoint.post
    .in("real-time-queue-service" / "user")
    .in("add-user-and-subscribe")
    .in(jsonBody[UserSessionId])
    .out(
      webSocketBody[String, CodecFormat.TextPlain, PositionUpdate, CodecFormat.Json](Fs2Streams[F])
    ) // stream entire messages ignoring input stream
    // .out(streamBody(Fs2Streams[F])(Schema.derived[PositionUpdate], CodecFormat.Json()))
    // only allow stream of bytes, also can't signal end of stream
    .errorOut(statusCode and jsonBody[ErrorResponse])

  val userServiceServerEndpoint: ServerEndpoint[Fs2Streams[F] & capabilities.WebSockets, F] =
    userServiceEndpoint.serverLogicPure(useSessionId => endpointToServerWebsocket(useSessionId))

  val workerServiceEndpoint = endpoint.get
    .in("real-time-queue-service" / "worker")
    .in("get-next-user")
    .out(jsonBody[Option[UserPosition]]) // error cases?

  val workerServiceServerEndpoint = workerServiceEndpoint.serverLogicSuccess(_ => workerService.getNextUser)

  val userServiceRoute = Http4sServerInterpreter[F]().toWebSocketRoutes(userServiceServerEndpoint)

  val workerServiceRoute = Http4sServerInterpreter[F]().toRoutes(workerServiceServerEndpoint)
}

object Routes {
  def live[F[_] : Async](userService: UserService[F], workerService: WorkerService[F]): Routes[F] =
    new Routes[F](userService, workerService)
}
