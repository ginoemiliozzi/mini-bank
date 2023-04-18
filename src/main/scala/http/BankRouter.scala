package http

import akka.http.scaladsl.server.Directives._
import actors.PersistentBankAccount._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.data.Validated
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import http.Payloads.{CreateAccountPayload, UpdateAccountPayload}
import http.Validation.{Validator, validateEntity}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class BankRouter(mainBankActor: ActorRef[Command])(implicit
    system: ActorSystem[_]
) {

  implicit val timeout: Timeout = Timeout(3.seconds)

  def validateRequest[R: Validator](req: R)(routeIfValid: Route): Route =
    validateEntity(req) match {
      case Validated.Valid(_) => routeIfValid
      case Validated.Invalid(errors) =>
        complete(
          StatusCodes.BadRequest,
          errors.toList.map(_.errorMessage).mkString(" - ")
        )
    }
  /*
    POST /bank
      Payload: bank account creation request
      Response:
        201 Created
          Location: /bank/:uuid
        400 Bad Request

    GET /bank/:uuid
      Response:
       200 Ok
        Bank acc as JSON
       404 Not Found

    PUT /bank/:uuid
      Payload: (currency, amount) as json
      Response:
        200 Ok
          Account updated as JSON
        400 Bad request
        404 Not Found
   */
  val routes = pathPrefix("bank") {
    pathEndOrSingleSlash {
      post {
        entity(as[CreateAccountPayload]) { createRequest =>
          validateRequest(createRequest) {
            onSuccess(createBankAccount(createRequest)) {
              case BankAccountCreatedResp(accountId) =>
                respondWithHeader(Location(s"/bank/$accountId")) {
                  complete(StatusCodes.Created)
                }
            }
          }
        }
      }
    } ~
      path(Segment) { accId =>
        get {
          onSuccess(getBankAccount(accId)) {
            case GetBankAccountResp(Some(account)) =>
              complete(account)
            case _ =>
              complete(StatusCodes.NotFound, s"Bank account $accId not found")
          }
        } ~
          put {
            entity(as[UpdateAccountPayload]) { updateRequest =>
              validateRequest(updateRequest) {
                onSuccess(updateBankAccount(accId, updateRequest)) {
                  case BankAccountBalanceUpdatedResp(Success(bankAcc)) =>
                    complete(bankAcc)
                  case BankAccountBalanceUpdatedResp(Failure(exception)) =>
                    complete(StatusCodes.BadRequest, exception.getMessage)
                  case _ =>
                    complete(
                      StatusCodes.NotFound,
                      s"Bank account $accId not found - not updated"
                    )
                }
              }
            }
          }
      }
  }

  private def createBankAccount(
      createRequest: CreateAccountPayload
  ): Future[Response] = {
    mainBankActor.ask(replyTo => createRequest.toCommand(replyTo))
  }

  private def getBankAccount(accId: String): Future[Response] = {
    mainBankActor.ask(replyTo => GetBankAccount(accId, replyTo))
  }

  private def updateBankAccount(
      accId: String,
      updateRequest: UpdateAccountPayload
  ): Future[Response] = {
    mainBankActor.ask(replyTo => updateRequest.toCommand(accId, replyTo))
  }
}
