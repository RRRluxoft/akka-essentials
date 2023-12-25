package part5patterns

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import utils._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}

object AskDemo {

  trait WorkProtocol
  case class ComputationalTask(payload: String, replyTo: ActorRef[WorkProtocol]) extends WorkProtocol
  case class ComputationalResult(result: Int)                                    extends WorkProtocol

  object Worker {
    def apply(): Behavior[WorkProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case ComputationalTask(text, replyTo) =>
          context.log.info(s"[worker] Crunching data for: $text")
          replyTo ! ComputationalResult(text.split(" ").length)
          Behaviors.same
        case _                                =>
          Behaviors.same
      }
    }
  }

  def askPatternSimple(): Unit = {
    // 1 - import the right package
    import akka.actor.typed.scaladsl.AskPattern._

    // 2 - set up some implicits
    val system = ActorSystem(Worker(), "DemoAskSimple").withFiniteLifespan(5.seconds)
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    // 3 - call the ask method
    val text = "Trying the ask pattern, seems convoluted"
    val reply: Future[WorkProtocol] = system.ask(ref => ComputationalTask(text, ref))

    // 4 - process the Future
    import system.executionContext
    reply.foreach(println)
  }

  def askFromWithinAnotherActor(): Unit = {
    val userGuardian = Behaviors.setup[WorkProtocol] { context =>
      val worker = context.spawn(Worker(), "worker")

      // 0 - define extra messages that I should handle as results of ask
      case class ExtendedComputationalResult(count: Int, description: String) extends WorkProtocol

      // 1 - set up the implicits
      implicit val timeout: Timeout = Timeout(3.seconds)

      // 2 - ask
      val text = "This ask pattern seems quite complicated"
      context.ask(worker, ref => ComputationalTask(text, ref)) { // lambda Try here
        // Try[WorkProtocol] => WorkProtocol message that will be sent TO ME later
        case Success(ComputationalResult(result)) => ExtendedComputationalResult(result, "This is pretty damn hard")
        case Failure(ex)                          => ExtendedComputationalResult(-1, s"Computation failed: $ex")
      }

      // 3 - handle the result (messages) from the ask pattern
      Behaviors.receiveMessage {
        case ExtendedComputationalResult(count, description) =>
          context.log.info(s"Ask and ye shall receive: $description - $count")
          Behaviors.same
        case _                                               =>
          Behaviors.same
      }
    }

    ActorSystem(userGuardian, "DemoAskConvoluted").withFiniteLifespan(5.seconds)
  }

  def main(args: Array[String]): Unit =
//    askPatternSimple()
    askFromWithinAnotherActor()

}
