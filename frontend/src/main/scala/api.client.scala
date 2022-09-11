package twotm8
package frontend

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import org.scalajs.dom
import org.scalajs.dom.Fetch.fetch
import org.scalajs.dom.RequestInit
import org.scalajs.dom.*
import org.scalajs.dom.experimental.ResponseInit
import sttp.client3.FetchBackend
import sttp.client3.SttpBackend
import sttp.tapir.Endpoint
import sttp.tapir.Endpoint.apply
import sttp.tapir.client.sttp.SttpClientInterpreter
import twotm8.api.ErrorInfo
import twotm8.api.ErrorInfo.Unauthorized
import twotm8.api.Payload
import twotm8.endpoints.*
import twotm8.frontend.RetryingBackend
import upickle.default.ReadWriter

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.Promise

object ApiClient extends ApiClient(using Stability())

class ApiClient(using Stability):
  import scala.concurrent.ExecutionContext.Implicits.global

  private val backend = RetryingBackend(FetchBackend())
  private val interpreter = SttpClientInterpreter()

  def get_profile(author: String, token: Option[Token]) = interpreter
    .toSecureClientThrowErrors(endpoints.get_thought_leader, None, backend)
    .apply(token.map(_.value))
    .apply(author)

  def me(token: Token) =
    interpreter
      .toSecureClientThrowDecodeFailures(endpoints.get_me, None, backend)
      .apply(token.value)
      .apply(())

  def get_wall(token: Token) =
    interpreter
      .toSecureClientThrowDecodeFailures(endpoints.get_wall, None, backend)
      .apply(token.value)
      .apply(())

  def register(payload: Payload.Register) =
    interpreter
      .toClientThrowDecodeFailures(endpoints.register, None, backend)
      .apply(payload)
      .map {
        case Right(_)        => None
        case Left(errorInfo) => Some(errorInfo.message)
      }

  def login(payload: Payload.Login) = interpreter
    .toClientThrowDecodeFailures(endpoints.login, None, backend)
    .apply(payload)

  def create(payload: Payload.Create, token: Token) =
    callEndpointWithPayloadAndToken(payload, endpoints.create_twot, token)

  def delete_twot(twotId: TwotId, token: Token) =
    interpreter
      .toSecureClientThrowDecodeFailures(endpoints.delete_twot, None, backend)
      .apply(token.value)
      .apply(twotId)

  def set_follow(payload: Payload.Follow, state: Boolean, token: Token) =
    val endpoint =
      if state then endpoints.add_follower else endpoints.delete_follower
    callEndpointWithPayloadAndToken(payload, endpoint, token)
  end set_follow

  def set_uwotm8(payload: Payload.Uwotm8, state: Boolean, token: Token) =
    val endpoint =
      if state then endpoints.add_uwotm8 else endpoints.delete_uwotm8
    callEndpointWithPayloadAndToken(payload, endpoint, token)
  end set_uwotm8

  private def callEndpointWithPayloadAndToken[INPUT, OUTPUT](
      payload: INPUT,
      endpoint: Endpoint[JWT, INPUT, ErrorInfo, OUTPUT, Any],
      token: Token
  ) =
    interpreter
      .toSecureClientThrowDecodeFailures(endpoint, None, backend)
      .apply(token.value)
      .apply(payload)
      .map {
        case Right(_)                    => Right(None)
        case Left(Unauthorized(message)) => Left(Unauthorized(message))
        case Left(errorInfo)             => Right(Some(errorInfo.message))
      }
  end callEndpointWithPayloadAndToken

end ApiClient
