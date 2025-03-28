package com.selinazjw.rtqs.routes

import cats.{ApplicativeError, MonadThrow}
import com.selinazjw.rtqs.service.UserService
import cats.effect.{Async, IO}
import cats.syntax.all.*
import com.selinazjw.rtqs.model.{ErrorResponse, InvalidArgument, PositionUpdate, UserSessionId}
import com.selinazjw.rtqs.service.UserServiceTapir
import com.selinazjw.rtqs.service.UserService
import io.circe.generic.auto.*
import io.circe.syntax._
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import fs2.Stream
import fs2.Pipe
import sttp.model.StatusCode

private final class Routes[F[_] : MonadThrow](userService: UserService[F]) {

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

  val userServiceEndpoint = endpoint.post
    .in("real-time-queue-service" / "user")
    .in("add-user-and-subscribe")
    .in(jsonBody[UserSessionId])
    .out(
      webSocketBody[String, CodecFormat.TextPlain, PositionUpdate, CodecFormat.Json](Fs2Streams[F])
    )                                                                     // stream entire messages ignoring input stream
    //.out(streamBody(Fs2Streams[F])(Schema.derived[PositionUpdate], CodecFormat.Json()))  
       // only allow stream of bytes, also can't signal end of stream
    .errorOut(statusCode and jsonBody[ErrorResponse])

  val userServiceServerEndpoint =
    userServiceEndpoint.serverLogicPure(useSessionId => endpointToServerWebsocket(useSessionId))
}
