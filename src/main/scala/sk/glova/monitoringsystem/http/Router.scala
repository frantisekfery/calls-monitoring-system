package sk.glova.monitoringsystem.http

import akka.http.scaladsl.server.Directives._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.http.scaladsl.server.Route
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import sk.glova.monitoringsystem.actors.PersistentCallModule.{AddCall, Call, CallAddedResponse, CallModuleCreatedResponse, Command, CreateCallModule, GetCallModule, GetCallModuleResponse, Response}
import sk.glova.monitoringsystem.actors.CallModuleManager.GetCallModules
import sk.glova.monitoringsystem.http.Validation.{Validator, validateEntity, validateRequired}

import scala.util.{Failure, Success}

case class CallModuleCreateRequest(source: String, protocol: String) {
  def toCommand(replyTo: ActorRef[Response]): Command =
    CreateCallModule(source, protocol, replyTo)
}

object CallModuleCreateRequest {
  implicit val validator: Validator[CallModuleCreateRequest] = (request: CallModuleCreateRequest) => {
    val source = validateRequired(request.source, "source")
    val protocol = validateRequired(request.protocol, "protocol")

    (source, protocol).mapN(CallModuleCreateRequest.apply)
  }
}

case class AddCallRequest(id: String, startOfCall: String, endOfCall: String, duration: String, callerNumber: String,
                          recipientNumber: String, status: String) {
  def toCommand(replyTo: ActorRef[Response]): Command =
    AddCall(id, Call(startOfCall, endOfCall, duration, callerNumber, recipientNumber, status), replyTo)
}

object AddCallRequest {
  implicit val validator: Validator[AddCallRequest] = (request: AddCallRequest) => {
    val id = validateRequired(request.id, "id")
    val startOfCall = validateRequired(request.startOfCall, "startOfCall")
    val endOfCall = validateRequired(request.endOfCall, "endOfCall")
    val duration = validateRequired(request.duration, "duration")
    val callerNumber = validateRequired(request.callerNumber, "callerNumber")
    val recipientNumber = validateRequired(request.recipientNumber, "recipientNumber")
    val status = validateRequired(request.status, "status")

    (id, startOfCall, endOfCall, duration, callerNumber, recipientNumber, status).mapN(AddCallRequest.apply)
  }
}

case class FailureResponse(reason: String)

class Router(callMonitor: ActorRef[Command])(implicit system: ActorSystem[_]) {
  implicit val timeout: Timeout = Timeout(5.seconds)

  def createCallModule(request: CallModuleCreateRequest): Future[Response] =
    callMonitor.ask(replyTo => request.toCommand(replyTo))

  def getCallModule(id: String): Future[Response] =
    callMonitor.ask(replyTo => GetCallModule(id, replyTo))

  def getCallModules: Future[List[String]] =
    callMonitor.ask(replyTo => GetCallModules(replyTo))

  def addCall(request: AddCallRequest): Future[Response] =
    callMonitor.ask(replyTo => request.toCommand(replyTo))

  def validateRequest[R: Validator](request: R)(routeIfValid: Route): Route =
    validateEntity(request) match {
      case Valid(_) =>
        routeIfValid
      case Invalid(failures) =>
        complete(StatusCodes.BadRequest, FailureResponse(failures.toList.map(_.errorMessage).mkString(", ")))
    }

  val routes: Route = pathPrefix("monitor") {
    pathPrefix("manager") {
      pathPrefix("module") {
        pathEndOrSingleSlash {
          post {
            entity(as[CallModuleCreateRequest]) { request =>
              validateRequest(request) {
                onSuccess(createCallModule(request)) {
                  case CallModuleCreatedResponse(id) =>
                    respondWithHeader(Location(s"/module/$id")) {
                      complete(StatusCodes.Created)
                    }
                }
              }
            }
          } ~
          get {
            complete {
              getCallModules
            }
          }
        } ~
        path(Segment) { id =>
          get {
            onSuccess(getCallModule(id)) {
              case GetCallModuleResponse(Some(module)) => complete(module)
              case GetCallModuleResponse(None) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Call module with id: $id cannot be found."))
            }
          }
        }
      } ~
      post {
        entity(as[AddCallRequest]) { request =>
          validateRequest(request) {
            onSuccess(addCall(request)) {
              case CallAddedResponse(maybeCallModule) => maybeCallModule match {
                case Failure(e) => complete(StatusCodes.BadRequest, FailureResponse(e.getMessage))
                case Success(value) =>
                  respondWithHeader(Location(s"/module/${value.id}"))
                  complete(StatusCodes.Created)
              }
            }
          }
        }
      }
    }
  }
}
