package com.selinazjw.rtqs

import cats.effect.IO
import com.selinazjw.rtqs.model.{ErrorResponse, PositionUpdate, UserSessionId}
import com.selinazjw.rtqs.service.UserService
import io.circe.generic.auto.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import fs2.Stream

object Main {
  
  val userServiceEndpoint = endpoint.post
    .in("real-time-time-queue-service" / "user")
    .in("add-user-and-subscribe")
    .in(jsonBody[UserSessionId])
    .out(streamBody(Fs2Streams[IO])(Schema.derived[PositionUpdate], CodecFormat.Json()))
    .errorOut(statusCode and jsonBody[ErrorResponse])

  val userServiceServerEndpoint(userService: UserService[IO]) = userServiceEndpoint.serverLogic { userSessionId =>
    userService.addUserAndSubscribe(userSessionId)
  }
}
