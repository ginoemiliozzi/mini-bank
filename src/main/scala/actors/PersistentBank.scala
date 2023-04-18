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
