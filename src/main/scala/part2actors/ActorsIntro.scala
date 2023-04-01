package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ActorsIntro {

  // part 1: behavior
  val simpleActorBehavior: Behavior[String] = Behaviors.receiveMessage { (msg: String) =>
    /* Behavior[String] */
    // do smth with behavior
    println(s"[simple actor] Ive received: $msg")

    // new Behavior
    Behaviors.same[String]
  }

  def demoSimpleActor() = {
    // part 2: instantiate
    val actorSystem = ActorSystem(SimpleActor_v3(), "FirstActorSystem")

    // part 3: comunicate!
    actorSystem ! "Im in Akka"
    actorSystem ! "Im in Akka again"

    // part 4: shut down
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  // refactor !!!
  object SimpleActor {
    def apply(): Behavior[String] = Behaviors.receiveMessage { (msg: String) =>
      /* Behavior[String] */
      // do smth with behavior
      println(s"[simple actor] Ive received: $msg")

      // new Behavior
      Behaviors.same[String]
    }
  }

  object SimpleActor_v2 {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"[simple actor v2] Ive received: $message")
      Behaviors.same
    }
  }

  object SimpleActor_v3 {
    def apply(): Behavior[String] = Behaviors.setup { context =>
      // actor 'private'
      // YOUR CODE HERE
      context.log.info(s"[simple actor v3] CODE HERE")

      // Behavior used for the FIRST msg
      Behaviors.receiveMessage { msg =>
        context.log.info(s"[simple actor v3] Ive received: $msg")
        Behaviors.same
      }
    }
  }

  /**
    * Exercises
    * 1. Define two "person" actor behaviors, which receive Strings:
    *  - "happy", which logs your message, e.g. "I've received ____. That's great!"
    *  - "sad", .... "That sucks."
    *  Test both.
    *
    * 2. Change the actor behavior:
    *  - the happy behavior will turn to sad() if it receives "Akka is bad."
    *  - the sad behavior will turn to happy() if it receives "Akka is awesome!"
    *
    * 3. Inspect my code and try to make it better.
    */

  object HappyPersonBehavior {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"I've received '$message'. That's great!")
      Behaviors.same
    }
  }
  object SadPersonBehavior   {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"I've received '$message'. That's suck!")
      Behaviors.same
    }
  }

  // better:
  object Person {
    def happy(): Behavior[String] = Behaviors.receive { (context, message) =>
      message match {
        case "Akka is bad." =>
          context.log.info(s"Dont you say anything bad about Akka!")
          sad()
        case _              =>
          context.log.info(s"I've received '$message'. That's great!")
          Behaviors.same
      }
    }

    def sad(): Behavior[String] = Behaviors.receive { (context, message) =>
      message match {
        case "Akka is awesome!" =>
          context.log.info(s"Cool! you change a mind. Happy now!")
          happy()
        case _                  =>
          context.log.info(s"I've received '$message'. That's suck!")
          Behaviors.same
      }
    }
    def apply(): Behavior[String] = happy()
  }

  def demoPersons(): Unit = {
    val actorSystem = ActorSystem(Person.apply(), "FirstActorSystem")

    // part 3: communicate!
    actorSystem ! "Im learning Akka!"
    actorSystem ! "Akka is awesome!"
    actorSystem ! "Akka is bad."
    actorSystem ! "Akka is bad."
    actorSystem ! "Akka is awesome!"
    actorSystem ! "Akka is awesome!"
    actorSystem ! "Akka is bad."

    // part 4: shut down
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  object WeirdActor {
    // wants to receive messages of type Int AND String
    def apply(): Behavior[Any] = Behaviors.receive { (context, message) =>
      message match {
        case number: Int    =>
          context.log.info(s"I've received an int: $number")
          Behaviors.same
        case string: String =>
          context.log.info(s"I've received a String: $string")
          Behaviors.same
        case _              =>
          context.log.warn("Smth wrong", new IllegalArgumentException("expected Int or String"))
          Behaviors.same
      }
    }
  }

  // solution: add wrapper types & type hierarchy (case classes/objects)
  object BetterActor {
    sealed trait Message
    case class StringMessage(str: String) extends Message
    case class IntMessage(int: Int)       extends Message

    def apply(): Behavior[Message] = Behaviors.receive { (context, msg) =>
      msg match {
        case StringMessage(str) =>
          context.log.info(s"ve got a msg String: $str")
          Behaviors.same
        case msg @ IntMessage(int)   =>
          context.log.info(s"ve got a : $msg")
          Behaviors.same
      }
    }
  }

  def demoMessages() = {
    import BetterActor._
    val actorSystem = ActorSystem(BetterActor(), "MessagesActorSystem")
    actorSystem ! StringMessage("hello")
    actorSystem ! StringMessage("Scala!")
    actorSystem ! IntMessage(42)

    // shut down
    Thread.sleep(1500)
    actorSystem.terminate()
  }

  def main(args: Array[String]): Unit =
    demoMessages()

}
