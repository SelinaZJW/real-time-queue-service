import RealTimeQueueServiceTapirSpec.TestContext
import cats.effect.IO
import cats.syntax.all.*
import cats.effect.testing.scalatest.AsyncIOSpec
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService}
import com.dimafeng.testcontainers.scalatest.TestContainerForEach
import com.selinazjw.rtqs.model.{UserPosition, UserSessionId}
import fs2.{Pipe, Stream}
import org.http4s.circe.jsonOf
import io.circe.*
import io.circe.generic.auto.*
import org.http4s.Uri.{Authority, Host, Scheme}
import org.http4s.{EntityDecoder, Uri as Http4sUri}
import org.http4s.implicits.uri as http4sUri
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Forwarded.Node.Port
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.client4.{UriContext, WebSocketStreamBackend}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.model.Uri
import sttp.ws.WebSocketFrame
import sttp.client4.ws.stream.*

import java.io.File
import scala.concurrent.duration.DurationInt

class RealTimeQueueServiceTapirSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with TestContainerForEach {
  override val containerDef: DockerComposeContainer.Def = DockerComposeContainer.Def(
    composeFiles = new File("app-tapir/it/src/test/resources/docker-compose.yaml"),
    exposedServices = Seq(ExposedService("real-time-queue-service-tapir", 8080))
  )

  def withTestContext[A](test: TestContext => IO[A]): IO[A] = withContainers { containers =>
    val appTairHost  = containers.getServiceHost("real-time-queue-service-tapir", 8080)
    val appTapirPort = containers.getServicePort("real-time-queue-service-tapir", 8080) // retrieve random port assigned

    val userServiceBaseUri = uri"ws://$appTairHost:8080/real-time-queue-service/user"
    val workerServiceBaseUri = Http4sUri(Scheme.http.some).addSegment(s"$appTairHost:8080/real-time-queue-service/worker")

    val resources = for {
      httpClient      <- EmberClientBuilder.default[IO].build
      webSocketClient <- HttpClientFs2Backend.resource[IO]()
    } yield (httpClient, webSocketClient)

    resources.use((httpClient, webSocketClient) =>
      test(TestContext(httpClient, webSocketClient, userServiceBaseUri, workerServiceBaseUri)))
  }

  given EntityDecoder[IO, Option[UserPosition]] = jsonOf[IO, Option[UserPosition]]

  "RealTimeQueueService" should {
    "add users to queue, subscribe users to updates and serve next user to worker" in withTestContext {
      case TestContext(httpClient, webSocketClient, userServiceBaseUri, workerServiceBaseUri) =>
        def pipe: Pipe[IO, WebSocketFrame.Data[?], WebSocketFrame] = input =>
          input.flatMap {
            case WebSocketFrame.Text(text, _, _) =>
              println(s"Received text: $text")
              Stream.empty
            case WebSocketFrame.Binary(data, _, _) =>
              println(s"Received binary data: $data")
              Stream.empty
          }

        def userRequest(userSessionId: String) =
          basicRequest
            .get(userServiceBaseUri.addPath("add-user-and-subscribe").addParam("userSessionId", userSessionId))
            .response(asWebSocketStream(Fs2Streams[IO])(pipe))
            .send(webSocketClient)

        for {
          user1 <- userRequest("user1").start
          _     <- IO.sleep(2.seconds)
          user2 <- userRequest("user2").start

          _            <- IO.sleep(2.seconds)
          servingUser1 <- httpClient.expect(workerServiceBaseUri.addSegment("get-next-user"))

          _     <- IO.sleep(2.seconds)
          user3 <- userRequest("user3").start
          _     <- IO.sleep(2.seconds)
          user4 <- userRequest("user3").start

          _            <- IO.sleep(2.seconds)
          servingUser2 <- httpClient.expect(workerServiceBaseUri.addSegment("get-next-user"))
          _            <- IO.sleep(2.seconds)
          servingUser3 <- httpClient.expect(workerServiceBaseUri.addSegment("get-next-user"))
          _            <- IO.sleep(2.seconds)
          servingUser4 <- httpClient.expect(workerServiceBaseUri.addSegment("get-next-user"))
        } yield {
          servingUser1 shouldBe UserPosition(UserSessionId("user1"), 1).some
          servingUser2 shouldBe UserPosition(UserSessionId("user2"), 2).some
          servingUser3 shouldBe UserPosition(UserSessionId("user3"), 3).some
          servingUser4 shouldBe UserPosition(UserSessionId("user4"), 4).some
        }
    }
  }
}

object RealTimeQueueServiceTapirSpec {
  case class TestContext(
      httpClient: Client[IO],
      webSocketClient: WebSocketStreamBackend[IO, Fs2Streams[IO]],
      userServiceBaseUri: Uri,
      workerServiceBaseUri: Http4sUri
  )
}
