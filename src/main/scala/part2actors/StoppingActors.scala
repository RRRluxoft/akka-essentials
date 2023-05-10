package part2actors

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object StoppingActors {

  object SensitiveActor {
    def apply(): Behavior[String] = Behaviors.receive[String] { (context, message) =>
      context.log.info(s"Received: $message")
      if (message == "you're ugly") {
        // optionally pass () => Unit :
        Behaviors.stopped //(() => context.log.info(s"I'm stopped now ${context.self.path.name}"))
      } else Behaviors.same
    }
      .receiveSignal {
        case (context, PostStop) =>
          // clean up resources
          context.log.info(s"I'm stopped now '${context.self.path.name}'")
          Behaviors.same
      }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val sensitiveActor = context.spawn(SensitiveActor(), "sensitiveActor")

      sensitiveActor ! "Hi"
      sensitiveActor ! "how are you?"
      sensitiveActor ! "you're ugly"
      sensitiveActor ! "Hi there"

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "sensitive")
    Thread.sleep(1000)
    system.terminate()
  }

}
