package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ChildActorsExercise {

  /**
    * Exercise: distributed word counting
    *    requester ----- (computational task) ----> WCM ------ (computational task) ----> one child of type WCW
    *    requester <---- (computational res) <---- WCM ------ (computational res) <----
    *
    *  Scheme for scheduling tasks to children: round robin
    *  [1-10]
    *  task 1 - child 1
    *  task 2 - child 2
    *  .
    *  .
    *  .
    *  task 10 - child 10
    *  task 11 - child 1
    *  task 12 - child 2
    *  .
    *  .
    */

  trait MasterProtocol
  trait WorkerProtocol
  trait UserProtocol

  // master messages
  case class Initialize(nChildren: Int)                                   extends MasterProtocol
  case class WordCountTask(text: String, replyTo: ActorRef[UserProtocol]) extends MasterProtocol
  case class WordCountReply(id: Int, count: Int)                          extends MasterProtocol

  // worker messages
  case class WorkerTask(id: Int, text: String) extends WorkerProtocol

  // requester (user) messages
  case class Reply(count: Int) extends UserProtocol

  object WordCounterMaster {
    def apply(): Behavior[MasterProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case Initialize(nChildren) =>
          context.log.info(s"[master] initializing with $nChildren children")
          val childRefs = for {
            i <- 1 to nChildren
          } yield context.spawn(WordCounterWorker(context.self), s"worker-$i")

          active(childRefs, 0, 0, Map())

        case _ =>
          context.log.info(s"[master] Command not support while idle")
          Behaviors.same[MasterProtocol]
      }
    }

    def active(
      childRefs: Seq[ActorRef[WorkerProtocol]],
      currentChildIndex: Int,
      currentTaskId: Int,
      requestMap: Map[Int, ActorRef[UserProtocol]]
    ): Behavior[MasterProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case WordCountTask(text, replyTo) =>
          context.log.info(
            s"""[master] Ive received: $text
               |I will send it to the child #$currentChildIndex""".stripMargin)
          // prep
          val task = WorkerTask(currentTaskId, text)
          val childRef = childRefs(currentChildIndex)
          // act
          childRef ! task

          val nextChildIndex = (currentChildIndex + 1)     % childRefs.length
          val nextTaskId = currentTaskId + 1
          val newRequestMap = requestMap + (currentTaskId -> replyTo)
          active(childRefs, nextChildIndex, nextTaskId, newRequestMap)

        case WordCountReply(id, count) =>
          context.log.info(s"[master] Ive received a reply for task id #$id with $count")
          val originalSender = requestMap(id)
          originalSender ! Reply(count)
          active(childRefs, currentChildIndex, currentTaskId, requestMap - id)
      }
    }
  }
  object WordCounterWorker {
    def apply(masterRef: ActorRef[MasterProtocol]): Behavior[WorkerProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case WorkerTask(id, text) =>
          context.log.info(
            s"""[worker] [${context.self.path}]
               |Ive received task #$id
               |with text : '$text'
               |""".stripMargin)
          val result: Int = text.split(" ").length
          masterRef ! WordCountReply(id, result)
          Behaviors.same

        case _ =>
          context.log.info(s"[${context.self.path}] Command unsupported!")
          Behaviors.same
      }
    }
  }

  object Aggregator {
    def apply(): Behavior[UserProtocol] = active()

    def active(totalWords: Int = 0): Behavior[UserProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case Reply(count) =>
          context.log.info(s"[aggregator] Ive received $count, total is ${totalWords + count}")
          active(totalWords + count)
        case _            => ???
      }
    }
  }

  def testWordCounter(): Unit = {
    val userGuardian: Behavior[Unit] = Behaviors.setup { context =>
      val aggregator = context.spawn(Aggregator(), "aggregator")
      val master = context.spawn(WordCounterMaster(), "master")

      master ! Initialize(3)
      master ! WordCountTask("Its my firm belief", aggregator)
      master ! WordCountTask("Im inclined to believe", aggregator)
      master ! WordCountTask("Scala is awesome", aggregator)
      master ! WordCountTask("Test round robbin", aggregator)
      master ! WordCountTask("Test round robbin 2", aggregator)

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "WordCounter")
    Thread.sleep(1000)
    system.terminate()
  }

  def main(args: Array[String]): Unit = {
    testWordCounter()
  }

}
