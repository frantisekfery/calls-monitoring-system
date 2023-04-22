package sk.glova.monitoringsystem.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import sk.glova.monitoringsystem.actors.PersistentCallModule.{AddCall, CallAddedResponse, Command, CreateCallModule, Event, GetCallModule, GetCallModuleResponse}

import java.util.UUID
import scala.util.Failure

object CallModuleManager {

  case class GetCallModules(replyTo: ActorRef[List[String]]) extends Command
  case class NewCallModuleIdStored(id: String) extends Event
  case class State(callModulesIds: Map[String, ActorRef[Command]])

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) =>
    command match {
      case create@CreateCallModule(_, _, _) =>
        val id = UUID.randomUUID().toString
        context.log.info(s"Persisting call module ID: $id into module manager state.")
        Effect
          .persist(NewCallModuleIdStored(id))
          .thenReply(context.spawn(PersistentCallModule(id), id))(_ => create)
      case updateCmd@AddCall(id, _, replyTo) =>
        state.callModulesIds.get(id) match {
          case Some(module) => Effect.reply(module)(updateCmd)
          case None => Effect.reply(replyTo)(CallAddedResponse(Failure(new RuntimeException("Call Module cannot be found"))))
        }
      case getCmd@GetCallModule(id, replyTo) =>
        state.callModulesIds.get(id) match {
          case Some(module) => Effect.reply(module)(getCmd)
          case None => Effect.reply(replyTo)(GetCallModuleResponse(None))
        }
      case GetCallModules(replyTo) =>
        context.log.info(s"Fetching IDs of all call modules")
        Effect.reply(replyTo)(state.callModulesIds.keys.toList)
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) =>
    event match {
      case NewCallModuleIdStored(id) =>
        val callModule = context.child(id) // exists after command handler,
          .getOrElse(context.spawn(PersistentCallModule(id), id)) // does NOT exist in the recovery mode, so needs to be created
          .asInstanceOf[ActorRef[Command]]
        state.copy(state.callModulesIds + (id -> callModule))
    }

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("module-manager-id"),
      emptyState = State(Map()),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }
}
