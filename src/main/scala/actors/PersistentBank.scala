package actors

import actors.PersistentBankAccount.{
  BankAccountCreatedResp,
  CreateBankAccount,
  GetBankAccount,
  GetBankAccountResp,
  Response
}
import akka.NotUsed
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout
import cats.implicits.none

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Failure

object PersistentBank {
  import PersistentBankAccount._

  sealed trait BankEvent
  case class BankAccountCreated(accountId: String) extends BankEvent

  case class State(accounts: Map[String, ActorRef[Command]])

  def commandHandler(
      context: ActorContext[Command]
  ): (State, Command) => Effect[BankEvent, State] = (state, cmd) => {
    cmd match {
      case createCmd @ CreateBankAccount(_, _, _, _) =>
        val id = UUID.randomUUID().toString
        val newBankAccountRef = spawnAccountActor(id, context)
        Effect
          .persist(BankAccountCreated(id))
          .thenReply(newBankAccountRef)(_ => createCmd)

      case updateCmd @ UpdateBalance(accountId, _, _, replyTo) =>
        state.accounts.get(accountId) match {
          case Some(bankAccountRef) =>
            Effect.reply(bankAccountRef)(updateCmd)
          case None =>
            Effect.reply(replyTo)(
              BankAccountBalanceUpdatedResp(
                Failure(new RuntimeException("Account not found"))
              )
            )
        }

      case getCmd @ GetBankAccount(accountId, replyTo) =>
        state.accounts.get(accountId) match {
          case Some(bankAccountRef) =>
            Effect.reply(bankAccountRef)(getCmd)
          case None =>
            Effect.reply(replyTo)(GetBankAccountResp(none))
        }
    }
  }

  def eventHandler(
      context: ActorContext[Command]
  ): (State, BankEvent) => State = (state, event) => {
    event match {
      case BankAccountCreated(accountId) =>
        val bankAccountRef = context
          .child(actorAccountName(accountId))
          .getOrElse(
            spawnAccountActor(accountId, context)
          )
          .asInstanceOf[ActorRef[Command]]
        state.copy(accounts = state.accounts + (accountId -> bankAccountRef))
    }
  }

  def apply(): Behavior[Command] = Behaviors.setup(context => {
    EventSourcedBehavior[Command, BankEvent, State](
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = State(Map.empty),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  })

  private def actorAccountName(accId: String): String = s"account-$accId"
  private def spawnAccountActor(
      accId: String,
      context: ActorContext[Command]
  ): ActorRef[Command] =
    context.spawn(PersistentBankAccount(accId), actorAccountName(accId))
}

object BankPlayGround {
  def main(args: Array[String]): Unit = {
    val rootBehavior: Behavior[NotUsed] = Behaviors.setup(context => {

      implicit val timeOut: Timeout = Timeout(2.seconds)
      implicit val scheduler: Scheduler = context.system.scheduler
      implicit val ec: ExecutionContext = context.executionContext
      val logger = context.log

      val respHandler = context.spawn(
        Behaviors.receiveMessage[Response] {
          case BankAccountCreatedResp(accountId) =>
            logger.info(s"Account created: $accountId")
            Behaviors.same
          case GetBankAccountResp(bankAccountO) =>
            logger.info(s"Account details obtained: $bankAccountO")
            Behaviors.same
        },
        "replyHandler"
      )

      val bankRef = context.spawn(PersistentBank(), "bank")

      //bankRef ! CreateBankAccount("joe", "USD", 10.0, respHandler)
      bankRef ! GetBankAccount(
        "6f59d238-218f-4578-9ac1-8aaa5db03e1d",
        respHandler
      )

      Behaviors.empty
    })

    val actorSystem = ActorSystem(rootBehavior, "BankDemo")
  }
}
