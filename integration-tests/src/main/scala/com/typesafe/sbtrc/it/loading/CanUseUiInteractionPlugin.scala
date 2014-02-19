package com.typesafe.sbtrc
package it
package loading

import sbt.client._
import sbt.protocol._

import concurrent.duration.Duration.Inf
import concurrent.Await

// Tests using interaction...
class CanUseUiInteractionPlugin extends SbtClientTest {
  // TODO - Don't hardcode sbt versions, unless we have to...
  val dummy = utils.makeDummySbtProject("interactions", "0.13.1")

  // TODO - Add sbt-ui-plugin correctly...

  sbt.IO.write(new java.io.File(dummy, "interaction.sbt"),

    """|import sbt.UIContext.uiContext
       |
       | val readInput = taskKey[Unit]("Quick interaction with server test.")
       |  
       | readInput := {
       |   val contex = (uiContext in Global).?.value
       |   contex match {
       |     case Some(ctx) =>
       |       if(!ctx.confirm("test-confirm")) sys.error("COULD NOT CONFIRM TEST!")
       |       val line = ctx.readLine("> ", false)
       |       if(line != Some("test-line")) sys.error("COULD NOT READ LINE! - Got " + line)
       |       ()
       |     // This happens if server isn't loaded.
       |     case None => sys.error("NO UI CONTEXT DEFINED!")
       |   }
       | }
       |""".stripMargin)

  withSbt(dummy) { client =>
    // Here we request something to run which will ask for input...
    import concurrent.ExecutionContext.global
    object interaction extends Interaction {
      def readLine(prompt: String, mask: Boolean): Option[String] = Some("test-line")
      def confirm(msg: String): Boolean = {
        if (msg == "test-confirm") true
        else false
      }
    }
    val taskResult = concurrent.promise[Boolean]
    (client handleEvents {
      case TaskFinished(key, result) =>
        if (key.key.name == "readInput") {
          taskResult.success(result)
        }
      case _ => ()
    })(global)
    // Here we want to wait for the task to be done.
    client.requestExecution("readInput", Some(interaction -> global))
    assert(Await.result(taskResult.future, defaultTimeout), "Failed to interact with sbt task!")
  }

}