package sk.glova.monitoringsystem.app

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.http.scaladsl.Http
import akka.util.Timeout
import sk.glova.monitoringsystem.actors.PersistentCallModule.Command
import sk.glova.monitoringsystem.actors.CallModuleManager
import sk.glova.monitoringsystem.http.Router

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object App {

  def startHttpServer(app: ActorRef[Command])(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    val router = new Router(app)
    val routes = router.routes

    val httpBindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    httpBindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
      case Failure(ex) =>
        system.log.error(s"Failed to bind HTTP server, because of $ex")
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {

    val rootBehaviour = Behaviors.setup[Nothing] { context =>
      val moduleManager = context.spawn(CallModuleManager(), "module-manager")

      startHttpServer(moduleManager)(context.system)

      Behaviors.empty

    }

    ActorSystem[Nothing](rootBehaviour, "CallMonitorSystem")
  }
}
