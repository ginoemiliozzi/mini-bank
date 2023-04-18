package app

import actors.PersistentBank
import actors.PersistentBankAccount._
import akka.NotUsed
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.util.Timeout
import http.BankRouter

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object BankApp {

  def startHttpServer(bankActor: ActorRef[Command])(implicit
      system: ActorSystem[_]
  ): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    val router = new BankRouter(bankActor)
    val routes = router.routes
    val httpBindingF = Http().newServerAt("localhost", 8081).bind(routes)
    httpBindingF.onComplete {
      case Success(binding) =>
        system.log.info(s"Server online at ${binding.localAddress}")
      case Failure(exception) =>
        system.log.error(
          s"Failed to bind http server - Error ${exception.getMessage}"
        )
    }
  }

  def main(args: Array[String]): Unit = {
    trait RootCommand
    case class RetrieveBankActor(replyTo: ActorRef[ActorRef[Command]])
        extends RootCommand

    val rootBehavior: Behavior[RootCommand] = Behaviors.setup(context => {
      val bankActor = context.spawn(PersistentBank(), "bank")
      Behaviors.receiveMessage { case RetrieveBankActor(replyTo) =>
        replyTo ! bankActor
        Behaviors.same
      }
    })

    implicit val system: ActorSystem[RootCommand] =
      ActorSystem(rootBehavior, "bank-system")

    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val ec: ExecutionContext = system.executionContext

    val bankActorF: Future[ActorRef[Command]] =
      system.ask(replyTo => RetrieveBankActor(replyTo))

    bankActorF.foreach(startHttpServer)
  }
}
