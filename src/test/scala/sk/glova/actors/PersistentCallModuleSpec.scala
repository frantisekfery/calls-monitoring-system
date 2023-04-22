package sk.glova.actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import sk.glova.monitoringsystem.actors.PersistentCallModule
import sk.glova.monitoringsystem.actors.PersistentCallModule.{CallModuleCreatedResponse, CreateCallModule, Response}

class PersistentCallModuleSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "PersistentTelephoneModule actor" must {

    "reply with empty list if no call was added" in {
      val responseProbe = createTestProbe[Response]()
      val deviceActor = spawn(PersistentCallModule("id"))

      deviceActor ! CreateCallModule("source", "protocol", responseProbe.ref)
      responseProbe.expectMessage(CallModuleCreatedResponse("id"))
    }
  }
}
