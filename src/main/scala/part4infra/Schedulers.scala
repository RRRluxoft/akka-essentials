package part4infra

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.Cancellable
import utils.LoggerActor

import scala.concurrent.duration._

object Schedulers {



  def demoScheduler(): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val loggerActor = context.spawn(LoggerActor(), "loggerActor")

      context.log.info("[system] System starting")
      context.scheduleOnce(1.second, loggerActor, "reminder msg here")

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoScheduler")

    import system.executionContext
    system
      .scheduler
      .scheduleOnce(2.seconds, () => system.terminate())
  }

  // timeout pattern
  def demoActorWithTimeout(): Unit = {
    val timeoutActor: Behavior[String] = Behaviors.receive { (context, message) =>
      context.scheduleOnce(1.seconds, context.self, "timeout")

      message match {
        case "timeout" =>
          context.log.info("Stopping!")
          Behaviors.stopped
        case _         =>
          context.log.info(s"Received $message")
          Behaviors.same
      }
    }

    val system = ActorSystem(timeoutActor, "TimeoutDemo")
    system ! "trigger"
    Thread.sleep(2000)
    system ! "are you there?"
  }

  /**
    * Exercise: enhance the timeoutActor to reset its timer with every new message (except the "timeout" message)
    */
  object ResettingTimeoutActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"Received: $message")
      resettingTimeoutActor(context.scheduleOnce(1.second, context.self, "timeout"))
    }

    def resettingTimeoutActor(schedule: Cancellable): Behavior[String] = Behaviors.receive { (context, message) =>
      // val scheduler = context.scheduleOnce(1.seconds, context.self, "timeout")
      message match {
        case "timeout" =>
          context.log.info("Stopping!")
          Behaviors.stopped

        case _ =>
          context.log.info(s"Received: $message")
          // reset scheduler
          schedule.cancel()
          // Behaviors.same
          resettingTimeoutActor(context.scheduleOnce(1.seconds, context.self, "timeout"))
      }
    }
  }

  def demoActorResettingTimeout(): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val resettingTimeoutActor = context.spawn(ResettingTimeoutActor(), "resetter")

      resettingTimeoutActor ! "start timer"
      Thread.sleep(500)
      resettingTimeoutActor ! "reset"
      Thread.sleep(700)
      resettingTimeoutActor ! "this should still be visible"
      Thread.sleep(1200)
      resettingTimeoutActor ! "this should NOT be visible"

      Behaviors.empty
    }

    import utils._
    val system = ActorSystem(userGuardian, "DemoResettingTimeoutActor")
      .withFiniteLifespan(4.seconds)
//    import system.executionContext
//    system.scheduler.scheduleOnce(4.seconds, () => system.terminate())
  }

  def main(args: Array[String]): Unit =
    demoActorResettingTimeout()
}
