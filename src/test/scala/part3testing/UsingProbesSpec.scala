package part3testing

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpecLike

class UsingProbesSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import UsingProbesSpec._

  "A master actor" should {
    "register a worker" in {
      val master = testKit.spawn(Master(), "master")
      val workerProbe = testKit.createTestProbe[WorkerTask]()
      val externalProbe = testKit.createTestProbe[ExternalProtocol]()

      master ! Register(workerProbe.ref, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)
    }

    "send a task to the worker actor" in {
      val master = testKit.spawn(Master())
      val workerProbe = testKit.createTestProbe[WorkerTask]()
      val externalProbe = testKit.createTestProbe[ExternalProtocol]()

      master ! Register(workerProbe.ref, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)

      val taskString = "I like to be there"
      master ! Work(taskString, externalProbe.ref)

      workerProbe.expectMessage(WorkerTask(taskString, master.ref, externalProbe.ref))
      // mocking the interaction with the worker actor
      master ! WorkCompleted(5, externalProbe.ref)
      externalProbe.expectMessage(Report(5))
    }

    "aggregate data correctly" in {
      val master = testKit.spawn(Master())
      val externalProbe = testKit.createTestProbe[ExternalProtocol]()

      val mockedWorkerBehavior = Behaviors.receiveMessage[WorkerTask] {
        case WorkerTask(text, master, replyTo) =>
          val count: Int = text.split(" ").length
          master ! WorkCompleted(count, replyTo)
          Behaviors.same
      }

      val workerProbe = testKit.createTestProbe[WorkerTask]()
      val mockedWorker: ActorRef[WorkerTask] =
        testKit.spawn(Behaviors.monitor(workerProbe.ref, mockedWorkerBehavior))

      master ! Register(mockedWorker, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)

      val taskString = "I like to be there"
      master ! Work(taskString, externalProbe.ref)
      master ! Work(taskString, externalProbe.ref)

      externalProbe.expectMessage(Report(5))
      externalProbe.expectMessage(Report(10))
    }
  }

}


object UsingProbesSpec {

  /*
    requester -> master -> worker
              <-        <-
   */
  trait MasterProtocol
  case class Work(text: String, replyTo: ActorRef[ExternalProtocol]) extends MasterProtocol
  case class WorkCompleted(count: Int, originalDestination: ActorRef[ExternalProtocol]) extends MasterProtocol
  case class Register(workerRef: ActorRef[WorkerTask], replyTo: ActorRef[ExternalProtocol]) extends MasterProtocol

  case class WorkerTask(text: String, master: ActorRef[MasterProtocol], originalDestination: ActorRef[ExternalProtocol])


  trait ExternalProtocol
  case class Report(totalCount: Int) extends ExternalProtocol
  case object RegisterAck extends ExternalProtocol

  object Master {
    def apply(): Behavior[MasterProtocol] = Behaviors.receiveMessage {
      case Register(workerRef, replyTo) =>
        replyTo ! RegisterAck
        active(workerRef)
      case _ =>
        Behaviors.same
    }

    def active(workerRef: ActorRef[WorkerTask], totalCount: Int = 0): Behavior[MasterProtocol] =
      Behaviors.receive { (context, msg) =>
        msg match {
          case Work(text, replyTo) =>
            workerRef ! WorkerTask(text, context.self, replyTo)
            Behaviors.same
          case WorkCompleted(count, originalDestination) =>
            val newTotalCount = totalCount + count
            originalDestination ! Report(newTotalCount)
            active(workerRef, newTotalCount)
          case Register(workerRef, replyTo) => ???
          case _ => ???
        }
      }

  }

}