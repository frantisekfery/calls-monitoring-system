package sk.glova.monitoringsystem.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.util.{Success, Try}

object PersistentCallModule {

  trait Command //TODO sealed
  case class CreateCallModule(source: String, protocol: String, replyTo: ActorRef[Response]) extends Command
  case class AddCall(id: String, call: Call, replyTo: ActorRef[Response]) extends Command
  case class GetCallModule(id: String, replyTo: ActorRef[Response]) extends Command

  trait Event
  case class CallModuleCreated(callModule: CallModule) extends Event
  case class CallAdded(call: Call) extends Event
  case class Call(startOfCall: String, endOfCall: String, duration: String, callerNumber: String,
                  recipientNumber: String, status: String)

  case class CallModule(id: String, source: String, protocol: String, calls: List[Call])

  trait Response
  case class CallModuleCreatedResponse(id: String) extends Response
  case class CallAddedResponse(maybeCallModule: Try[CallModule]) extends Response
  case class GetCallModuleResponse(maybeCallModule: Option[CallModule]) extends Response

  def commandHandler(context: ActorContext[Command]): (CallModule, Command) => Effect[Event, CallModule] = (state, command) =>
    command match {
      case CreateCallModule(source, protocol, manager) =>
        val id = state.id
        context.log.info(s"Persisting call module with details. Source is '$source' and protocol is '$protocol'.")
        Effect
          .persist(CallModuleCreated(CallModule(id, source, protocol, List.empty)))
          .thenReply(manager)(_ => CallModuleCreatedResponse(id))
      case AddCall(_, call, manager) =>
        context.log.info(s"Persisting call with details $call.")
        Effect
          .persist(CallAdded(call))
          .thenReply(manager)(newState => CallAddedResponse(Success(newState)))
      case GetCallModule(id, manager) =>
        context.log.info(s"Fetching call module by ID $id.")
        Effect.reply(manager)(GetCallModuleResponse(Some(state)))
    }

  def eventHandler(context: ActorContext[Command]): (CallModule, Event) => CallModule = (state, event) =>
    event match {
      case CallModuleCreated(callModule) => callModule
      case CallAdded(call) =>
        context.log.info(s"Adding new call $call into state.")
        state.copy(calls = call :: state.calls)
    }

  def apply(id: String): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, CallModule](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = CallModule(id, "", "", List.empty), // unused
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }
}
