package http

import actors.PersistentBankAccount.{
  Command,
  CreateBankAccount,
  Response,
  UpdateBalance
}
import akka.actor.typed.ActorRef
import cats.implicits._
import http.Validation.{
  ValidationResult,
  Validator,
  minAbs,
  notNegative,
  required
}

object Payloads {

  // Create account
  case class CreateAccountPayload(
      user: String,
      currency: String,
      initialBalance: Double
  ) {
    def toCommand(replyTo: ActorRef[Response]): Command =
      CreateBankAccount(user, currency, initialBalance, replyTo)
  }
  object CreateAccountPayload {
    implicit val validCreate: Validator[CreateAccountPayload] =
      new Validator[CreateAccountPayload] {
        override def validate(
            payload: CreateAccountPayload
        ): ValidationResult[CreateAccountPayload] = {
          val userValid = required(payload.user, "user")
          val currencyValid = required(payload.currency, "currency")
          val balanceValid =
            notNegative(payload.initialBalance, "initalBalance").combine(
              minAbs(payload.initialBalance, 0.01, "initialBalance")
            )

          (userValid, currencyValid, balanceValid).mapN(
            CreateAccountPayload.apply
          )
        }
      }
  }

  // Update account
  case class UpdateAccountPayload(
      currency: String,
      amount: Double
  ) {
    def toCommand(accId: String, replyTo: ActorRef[Response]): Command =
      UpdateBalance(accId, currency, amount, replyTo)
  }
  object UpdateAccountPayload {
    implicit val validUpdate: Validator[UpdateAccountPayload] =
      new Validator[UpdateAccountPayload] {
        override def validate(
            payload: UpdateAccountPayload
        ): ValidationResult[UpdateAccountPayload] = {
          val currencyValid = required(payload.currency, "currency")
          val amountValid = minAbs(payload.amount, 0.01, "amount")
          (currencyValid, amountValid).mapN(UpdateAccountPayload.apply)
        }
      }
  }
}
