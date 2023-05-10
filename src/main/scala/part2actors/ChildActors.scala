package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
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

    def apply(): Behavior[Command] = idle()

    def idle(): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[parent] Creating child wild name $name")
          val childRef: ActorRef[String] = context.spawn(Child(), name)
          active(childRef)
        case _                 => ???
      }
    }

    private def active(childRef: ActorRef[String]): Behavior[Command] = Behaviors.receive[Command] { (context, message) =>
      message match {
        case TellChild(msg) =>
          context.log.info(s"[parent] Sending message '$msg' to child")
          childRef ! msg
          Behaviors.same

        case StopChild =>
          context.log.info(s"[parent] stopping child")
          context.stop(childRef) // child ONLY !!!
          idle()

        case _ =>
          context.log.info(s"[parent] command not supported")
          Behaviors.same
      }
    }
  }

  object Child {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"[${context.self.path.name}] Received $message")
      Behaviors.same
    }
  }

  def demoParentChild(): Unit = {
    import Parent.{CreateChild, StopChild, TellChild}
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
//      context.spawn(???, "") <==>  ActorSystem(???, "name")
//      val parent = ActorSystem(Parent(), "DemoParentChildSystem")
      val parent = context.spawn(Parent(), "parent")
      parent ! CreateChild("ChildActor_1")
      parent ! TellChild("hey kid, you there?")
      parent ! StopChild
      parent ! CreateChild("ChildActor_2")
      parent ! TellChild("Yo, kid, Are you there?")
      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChildSystem")

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

    def apply(): Behavior[Command] = active(Map[String, ActorRef[String]]())

    private def active(children: Map[String, ActorRef[String]]): Behavior[Command] = Behaviors.receive { (context, message) =>
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

        case _ =>
          context.log.info(s"[parent] command not supported")
          Behaviors.same
      }
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
