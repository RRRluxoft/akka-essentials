package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ActorState {

  /*
    Exercise: use the setup method to create a word counter which
      - splits each message into words
      - keeps track of the TOTAL number of words received so far
      - log the current # of words + TOTAL # of words
   */

  object WordCounter {
    def apply(): Behavior[String] = Behaviors.setup { context =>
      var total = 0

      Behaviors.receiveMessage { message =>
        val newCount = message.split(" ").length
        total += newCount
        context.log.info(s"Message word count: $newCount - total count: $total")
        Behaviors.same
      }
    }
  }

  trait SimpleThing
  case object EatChocolate    extends SimpleThing
  case object CleanUpTheFloor extends SimpleThing
  case object LearnAkka       extends SimpleThing
  /*
    Message types must be IMMUTABLE and SERIALIZABLE.
    - use case classes/objects
    - use a flat type hierarchy
   */

  object SimpleHuman {
    def apply(): Behavior[SimpleThing] = Behaviors.setup { context =>
      var happiness = 0

      Behaviors.receiveMessage {
        case EatChocolate    =>
          context.log.info(s"[$happiness] Eating chocolate")
          happiness += 1
          Behaviors.same
        case CleanUpTheFloor =>
          context.log.info(s"[$happiness] Wiping the floor, ugh...")
          happiness -= 2
          Behaviors.same
        case LearnAkka       =>
          context.log.info(s"[$happiness] Learning Akka, YAY!")
          happiness += 99
          Behaviors.same
      }
    }
  }

  object SimpleHuman_V2 {
    def apply(): Behavior[SimpleThing] = doThings(0)

    private def doThings(happiness: Int = 0): Behavior[SimpleThing] = Behaviors.receive { (context, msg) =>
      msg match {
        case EatChocolate    =>
          context.log.info(s"[$happiness] Eating chocolate")
          doThings(happiness + 1)
        case CleanUpTheFloor =>
          context.log.info(s"[$happiness] Wiping the floor, ugh...")
          doThings(happiness - 2)
        case LearnAkka       =>
          context.log.info(s"[$happiness] Learning Akka, YAY!")
          doThings(happiness + 99)
      }
    }
  }

  /*
    Tips:
    - each var/mutable field becomes an immutable METHOD ARGUMENT
    - each state change = new behavior obtained by calling the method with a different argument
   */

  /**
   * Exercise: refactor the "stateful" word counter into a "stateless" version.
   */
  object WordCounter_V2 {
    def apply(): Behavior[String] = countWords(0)
    def countWords(total: Int): Behavior[String] = Behaviors.receive { (context, message) =>
      val newCount = message.split(" ").length
      context.log.info(s"Message word count: $newCount - total count: ${total + newCount}")
      countWords(total + newCount)
    }
  }

  def demoSimpleHuman(): Unit = {
    val human = ActorSystem(SimpleHuman_V2(), "DemoSimpleHuman")

    human ! LearnAkka
    human ! EatChocolate
    (1 to 30).foreach(_ => human ! CleanUpTheFloor)

    Thread.sleep(1000)
    human.terminate()
  }

  def demoWordsCounting() = {
    val system = ActorSystem(WordCounter_V2(), "DemoWordsCounting")

    system ! "Hello Akka world here"
    system ! "Hello Akka "
    system ! "Hello Akka from another world ! "

    Thread.sleep(1500)
    system.terminate()
  }
  def main(args: Array[String]): Unit =
    demoWordsCounting()
}
