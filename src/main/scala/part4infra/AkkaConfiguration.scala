package part4infra

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object AkkaConfiguration {

  object SimpleLoggingActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, msg) =>
      context.log.info(msg)
      Behaviors.same
    }
  }

  // 1 - inline configuration
  def demoInlineConfig(): Unit = {
    // HOCON, superset of JSON, managed by Lightbend
    val configString =
      """
        |akka {
        |  loglevel = "DEBUG"
        |}
        |""".stripMargin
    val config = ConfigFactory.parseString(configString)
    val system = ActorSystem(SimpleLoggingActor(), "ConfigDemo", ConfigFactory.load(config))

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 2 - config file : application.conf
  def demoConfigFile(): Unit = {
    val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
    val system = ActorSystem(SimpleLoggingActor(), "ConfigDemo", specialConfig)

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 3 - a different config in another file
  def demoSeparateConfigFile(): Unit = {
    val separateConfig = ConfigFactory.load("secretDir/secretConfiguration.conf")
    val system = ActorSystem(SimpleLoggingActor(), "ConfigDemo", ConfigFactory.load(separateConfig))

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 4 - different file formats (JSON, properties)
  def emoOtherFileFormats(): Unit = {
    val jsonConfig = ConfigFactory.load("json/jsonConfiguration.json")
    // properties format
    val propsConfig = ConfigFactory.load("properties/propsConfiguration.properties")

    println(s"properties config with custom property: ${propsConfig.getString("mySimpleProperty")}")
    println(s"properties config with Akka property: ${propsConfig.getString("akka.loglevel")}")

    val system = ActorSystem(SimpleLoggingActor(), "ConfigDemo", propsConfig)

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  def main(args: Array[String]): Unit =
    emoOtherFileFormats()

}
