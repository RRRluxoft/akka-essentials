package part3testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpecLike

class EssentialTestingSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import EssentialTestingSpec._

  "A simple actor" should {
    // test suite

    "send back a duplicated message" in {
      // code for testing
      val simpleActor = testKit.spawn(SimpleActor(), "simpleActor")
      val probe = testKit.createTestProbe[SimpleProtocol]() // "inspector"

      // scenario
      simpleActor ! SimpleMessage("TypedAkka_", probe.ref)

      // assertions
      probe.expectMessage(SimpleReply("TypedAkka_TypedAkka_"))
    }

    "A black hole actor" should {
      "not reply back" in {
        val blackHole = testKit.spawn(BlackHole(), "blackHole")
        val probe = testKit.createTestProbe[SimpleProtocol]("probe")

        blackHole ! SimpleMessage("I love zio", probe.ref)
        blackHole ! SimpleMessage("I love zio 2", probe.ref)
        blackHole ! SimpleMessage("I love zio 3", probe.ref)

        import scala.concurrent.duration._
        probe.expectNoMessage(3.seconds)
      }
    }
  }
}

object EssentialTestingSpec {
  // code under test
  sealed trait SimpleProtocol

  final case class SimpleMessage(message: String, sender: ActorRef[SimpleProtocol]) extends SimpleProtocol

  final case class UppercaseString(message: String, replyTo: ActorRef[SimpleProtocol]) extends SimpleProtocol

  final case class FavoriteTech(replyTo: ActorRef[SimpleProtocol]) extends SimpleProtocol

  final case class SimpleReply(contents: String) extends SimpleProtocol

  object SimpleActor {
    def apply(): Behavior[SimpleProtocol] = Behaviors.receiveMessage {
      case SimpleMessage(msg, replyTo) =>
        replyTo ! SimpleReply(msg + msg)
        Behaviors.same

      case UppercaseString(msg, replyTo) =>
        replyTo ! SimpleReply(msg.toUpperCase())
        Behaviors.same

      case FavoriteTech(replyTo) =>
        replyTo ! SimpleReply("Scala")
        replyTo ! SimpleReply("Akka")
        Behaviors.same
    }
  }

  object BlackHole {
    def apply(): Behavior[SimpleProtocol] = Behaviors.ignore
  }

}
