package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors

object ChildActors {

  /*
    - actors can create other actors (children): parent -> child -> grandChild -> ...
                                                        -> child2 -> ...
    - actor hierarchy = tree-like structure
    - root of the hierarchy = "guardian" actor (created with the ActorSystem)
    - actors can be identified via a path: /user/parent/child/grandChild/

    - ActorSystem creates
      - the top-level (root) guardian, with children
        - system guardian (for Akka internal messages)
        - user guardian (for our custom actors)

    - ALL OUR ACTORS are child actors of the user guardian
   */

  object Parent {
    trait Command
    case class CreateChild(name: String) extends Command
    case class TellChild(msg: String)    extends Command
    case object StopChild                extends Command
    case object WatchChild               extends Command

    def apply(): Behavior[Command] = idle()

    def idle(): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[parent] Creating child with name $name")
          // creating a child actor REFERENCE (used to send messages to this child)
          val childRef: ActorRef[String] = context.spawn(Child(), name)
          active(childRef)
        case _                 => ???
      }
    }

    private def active(childRef: ActorRef[String]): Behavior[Command] = Behaviors.receive[Command] { (context, message) =>
      message match {
        case TellChild(message) =>
          context.log.info(s"[parent] Sending message $message to child")
          childRef ! message // <- send a message to another actor
          Behaviors.same

        case StopChild =>
          context.log.info("[parent] stopping child")
          context.stop(childRef) // only works with CHILD actors
          idle()

        case WatchChild =>
          context.log.info(s"[parent] watching child")
          context.watch(childRef) // can use Any actor ref
          Behaviors.same

        case _ =>
          context.log.info(s"[parent] command not supported")
          Behaviors.same
      }
    }
    .receiveSignal {
      case (context, Terminated(childRefWhichDied)) =>
        context.log.info(s"[parent] Child ${childRefWhichDied.path} was killed by something...")
        idle()
    }
  }

  object Child {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"[${context.self.path.name}] Received $message")
      Behaviors.same
    }
  }

  def demoParentChild(): Unit = {
    import Parent._
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      // set up all the important actors in your application
      val parent = context.spawn(Parent(), "parent")
      // set up the initial interaction between the actors
      parent ! CreateChild("child")
      parent ! TellChild("hey kid, you there?")
      parent ! WatchChild
      parent ! StopChild
      parent ! CreateChild("child2")
      parent ! TellChild("yo new kid, how are you?")

      // user guardian usually has no behavior of its own
      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChild")
    Thread.sleep(1000)
    system.terminate()
  }

  /**
   * Exercise: write a Parent_V2 that can manage MULTIPLE child actors.
   */
  object Parent_V2 {
    trait Command
    case class CreateChild(name: String)            extends Command
    case class TellChild(name: String, msg: String) extends Command
    case class StopChild(name: String)              extends Command
    case class WatchChild(name: String)             extends Command

    def apply(): Behavior[Command] = active(Map())

    private def active(children: Map[String, ActorRef[String]]): Behavior[Command] = Behaviors.receive[Command] { (context, message) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[parent] Creating child '$name'")
          children.get(name)
            .fold {
              val childRef = context.spawn(Child(), name)
              active(children + (name -> childRef))
            } { _ =>
              context.log.warn(s"[parent] name '$name' should be unique")
//              active(children)
              Behaviors.same
            }

        case TellChild(name, msg) =>
          context.log.info(s"[parent] Sending message $msg to child $name")
          children.get(name)
            .fold {
              context.log.warn(s"[parent] actor '$name' doesnt exist")
            } { child =>
              child ! msg
            }
          Behaviors.same

        case StopChild(name) =>
          context.log.info(s"[parent] attempting to stop child with name $name")
          children.get(name)
            .fold(context.log.warn(s"[parent] actor '$name' doesnt exist")) { childRef =>
              context.stop(childRef)
            }
          active(children - name)

        case WatchChild(name) =>
          context.log.info(s"[parent] attempting to watch child with name $name")
          children.get(name)
            .fold(context.log.warn(s"[parent] actor '$name' doesnt exist")) { childRef =>
              context.watch(childRef)
            }
          Behaviors.same

        case _ =>
          context.log.info(s"[parent] command not supported")
          Behaviors.same
      }
    }
      .receiveSignal {
        case (context, Terminated(ref)) =>
          context.log.info(s"[parent] Child ${ref.path} was killed.")
          val childName = ref.path.name
          active(children - childName)
      }

  }

  def demoParentChild_V2(): Unit = {
    import Parent_V2._
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      //      context.spawn(???, "") <==>  ActorSystem(???, "name")
      //      val parent = ActorSystem(Parent(), "DemoParentChildSystem")
      val parent = context.spawn(Parent_V2(), "parent")
      parent ! CreateChild("ChildActor_1")
      parent ! CreateChild("ChildActor_1") // exists
      parent ! CreateChild("ChildActor_2")
      parent ! CreateChild("ChildActor_3")
      parent ! TellChild("ChildActor_1", "hey kid, you there?")
      parent ! TellChild("ChildActor_3", "hey kid, are you there?")
      parent ! TellChild("ChildActor_4", "hey kid, are you there?")
      parent ! Parent_V2.WatchChild("ChildActor_3")
      parent ! StopChild("ChildActor_3")
      parent ! TellChild("ChildActor_3", "Are you still there?")

      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChildSystem_V2")

    Thread.sleep(1000)
    system.terminate()
  }

  def main(args: Array[String]): Unit =
    demoParentChild_V2()

}
