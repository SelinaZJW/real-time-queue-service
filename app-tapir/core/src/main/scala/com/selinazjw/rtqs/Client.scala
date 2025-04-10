package com.selinazjw.rtqs

import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import com.selinazjw.rtqs.model.UserPosition
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.uri as http4sUri
import org.http4s.EntityDecoder
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import io.circe.*
import io.circe.generic.auto.*
import org.http4s.circe.*
import sttp.client4.*
import sttp.client4.ws.stream.*
import fs2.{Pipe, Stream}
import sttp.ws.WebSocketFrame

import scala.concurrent.duration.DurationInt

object Client extends IOApp.Simple {

  val workerServiceEndpoint = http4sUri"http://localhost:8080/real-time-queue-service/worker"
  val userServiceEndpoint   = uri"ws://localhost:8080/real-time-queue-service/user"

  val httpClient: Resource[IO, Client[IO]]                                      = EmberClientBuilder.default[IO].build
  val webSocketClient: Resource[IO, WebSocketStreamBackend[IO, Fs2Streams[IO]]] = HttpClientFs2Backend.resource[IO]()

  given EntityDecoder[IO, Option[UserPosition]] = jsonOf[IO, Option[UserPosition]]

  def program(httpClient: Client[IO], webSocketClient: WebSocketStreamBackend[IO, Fs2Streams[IO]]) =
    def pipe: Pipe[IO, WebSocketFrame.Data[?], WebSocketFrame] = input =>
      input.flatMap {
        case WebSocketFrame.Text(text, _, _) =>
          println(s"Received text: $text")
          Stream.empty
        case WebSocketFrame.Binary(data, _, _) =>
          println(s"Received binary data: $data")
          Stream.empty
      }

    def webSocketRequest(userSessionId: String) =
      basicRequest
        .get(userServiceEndpoint.addPath("add-user-and-subscribe").addParam("userSessionId", userSessionId))
        .response(asWebSocketStream(Fs2Streams[IO])(pipe))
        .send(webSocketClient)

    for {
      user1 <- webSocketRequest("user1").start
      _ <- IO.sleep(2.seconds)
      user2 <- webSocketRequest("user2").start

      _ <- IO.sleep(2.seconds)
      serveUser1 <- httpClient.expect(workerServiceEndpoint.addSegment("get-next-user")).map(println(_))

      _ <- IO.sleep(2.seconds)
      user3 <- webSocketRequest("user3").start

      _ <- IO.sleep(2.seconds)
      serveUser2 <- httpClient.expect(workerServiceEndpoint.addSegment("get-next-user")).map(println(_))
      _ <- IO.sleep(2.seconds)
      serveUser3 <- httpClient.expect(workerServiceEndpoint.addSegment("get-next-user")).map(println(_))
    } yield ()

  override def run: IO[Unit] = (httpClient, webSocketClient).parTupled.use (
    (httpClient, webSocketClient) =>
      program(httpClient, webSocketClient).handleErrorWith { error =>
        IO(println(s"Error: ${error.getMessage}"))
      }
  )
}
