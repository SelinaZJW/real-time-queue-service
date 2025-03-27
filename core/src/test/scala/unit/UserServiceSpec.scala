package unit

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.unsafe.IORuntime
import com.selinazjw.rtqs.model.{UserPosition, UserSessionId}
import com.selinazjw.rtqs.service.{QueueService, UserService}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import fs2.Stream

import scala.util.Random

class UserServiceSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

  def queueServiceMock(assignedPosition: Int, latestServedPosition: Int) = new QueueService[IO] {
    override def addUser(userSessionId: UserSessionId): IO[UserPosition] = IO(
      UserPosition(userSessionId, assignedPosition))

    override def nextUser: IO[Option[UserPosition]] = ???

    override def subscribeToUpdates(assignedPosition: Int): Stream[IO, Int] =
      Stream.emits(latestServedPosition to assignedPosition)
  }

  "UserService" should {
    "addUserAndSubscribe" in {
      val assignedPosition = Random.nextInt(10000)
      val latestServedPosition = Random.between(0, assignedPosition)
      val userService = UserService(queueServiceMock(assignedPosition, latestServedPosition))

      val stream = userService.addUserAndSubscribe(UserSessionId("test-user"))

      stream.compile.toList.asserting{_.map(_.position) shouldBe ((assignedPosition - latestServedPosition) to 0 by -1).toList}

    }
  }
}
