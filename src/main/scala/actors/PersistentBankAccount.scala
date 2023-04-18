package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import cats.implicits.{catsSyntaxOptionId, none}

import scala.util.{Failure, Success, Try}

object PersistentBankAccount {

  sealed trait Command
  case class CreateBankAccount(
      user: String,
      currency: String,
      initialBalance: Double,
      replyTo: ActorRef[Response]
  ) extends Command
  case class UpdateBalance(
      accountId: String,
      currency: String,
      amount: Double,
      replyTo: ActorRef[Response]
  ) extends Command
  case class GetBankAccount(
      accountId: String,
      replyTo: ActorRef[Response]
  ) extends Command

  trait Event
  case class BankAccountCreated(bankAccount: BankAccount) extends Event
  case class BalanceUpdated(amount: Double) extends Event

  case class BankAccount(
      id: String,
      user: String,
      currency: String,
      balance: Double
  )

  sealed trait Response
  case class BankAccountCreatedResp(accountId: String) extends Response
  case class BankAccountBalanceUpdatedResp(bankAccountT: Try[BankAccount])
      extends Response
  case class GetBankAccountResp(bankAccountO: Option[BankAccount])
      extends Response

  val commandHandler: (BankAccount, Command) => Effect[Event, BankAccount] =
    (state, cmd) => {
      cmd match {
        case CreateBankAccount(
              user,
              currency,
              initialBalance,
              bankActor
            ) =>
          val accountId = state.id
          val bankAccount =
            BankAccount(accountId, user, currency, initialBalance)
          Effect
            .persist(BankAccountCreated(bankAccount))
            .thenReply(bankActor)(_ => BankAccountCreatedResp(accountId))

        case UpdateBalance(
              _,
              _,
              amount,
              bankActor
            ) =>
          val newBalance = state.balance + amount
          if (newBalance < 0)
            Effect.reply(bankActor)(
              BankAccountBalanceUpdatedResp(
                Failure(
                  new RuntimeException("Cannot withdraw more than available")
                )
              )
            )
          else
            Effect
              .persist(BalanceUpdated(amount))
              .thenReply(bankActor)(newState =>
                BankAccountBalanceUpdatedResp(Success(newState))
              )
        case GetBankAccount(_, bankActor) =>
          Effect.reply(bankActor)(GetBankAccountResp(state.some))
      }
    }

  val eventHandler: (BankAccount, Event) => BankAccount = (state, event) => {
    event match {
      case BankAccountCreated(bankAccount) => bankAccount
      case BalanceUpdated(amount) =>
        state.copy(balance = state.balance + amount)
    }
  }

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, BankAccount](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = BankAccount(id, "not-used", "not-used", 0.0),
      commandHandler,
      eventHandler
    )
}
