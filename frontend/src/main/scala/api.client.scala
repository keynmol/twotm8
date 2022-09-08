package twotm8
package frontend

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import org.scalajs.dom
import org.scalajs.dom.Fetch.fetch
import org.scalajs.dom.*
import org.scalajs.dom.experimental.ResponseInit
import upickle.default.ReadWriter

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.Promise
import twotm8.frontend.Responses.ThoughtLeaderProfile
import org.scalajs.dom.RequestInit

object ApiClient extends ApiClient(using Stability())

class ApiClient(using Stability):
  import scala.scalajs.js
  import js.Thenable.Implicits.*
  import scala.concurrent.ExecutionContext.Implicits.global

  extension (req: Future[Response])
    def authenticated[A](f: Response => Future[A]): Future[Either[Error, A]] =
      req.flatMap { resp =>
        if resp.status == 401 then Future.successful(Left(Error.Unauthorized))
        else f(resp).map(Right.apply)
      }

  def get_profile(
      author: String,
      token: Option[Token]
  ): Future[Responses.ThoughtLeaderProfile] =
    exponentialFetch(
      s"/api/thought_leaders/$author",
      addAuth(new RequestInit {}, token)
    ).flatMap { resp =>
      resp
        .json()
        .map(fromJson[Responses.ThoughtLeaderProfile])
    }

  def me(tok: Token): Future[Either[Error, ThoughtLeaderProfile]] =
    val req = new RequestInit {}
    req.method = HttpMethod.GET
    exponentialFetch("/api/thought_leaders/me", addAuth(req, tok))
      .authenticated { resp =>
        resp.json().map(fromJson[Responses.ThoughtLeaderProfile])
      }

  def is_authenticated(token: Token): Future[Boolean] =
    me(token).map(_.isRight)

  def get_wall(token: Token) =
    exponentialFetch("/api/twots/wall", addAuth(new RequestInit {}, token))
      .authenticated { resp =>
        resp.json().map(fromJson[List[Responses.Twot]])
      }

  private def addAuth(rq: RequestInit, tok: Token) =
    rq.headers = js.Dictionary("Authorization" -> s"Bearer ${tok.value}")
    rq

  private def addAuth(rq: RequestInit, tokenMaybe: Option[Token]) =
    tokenMaybe.foreach { tok =>
      rq.headers = js.Dictionary("Authorization" -> s"Bearer ${tok.value}")
    }
    rq

  def login(
      payload: Payloads.Login
  ): Future[Either[String, Responses.TokenResponse]] =
    val req = new RequestInit {}
    req.method = HttpMethod.POST
    req.body = toJsonString(payload)

    exponentialFetch(s"/api/auth/login", req, forceRetry = true)
      .flatMap { resp =>
        if resp.ok then
          resp.json().map(fromJson[Responses.TokenResponse]).map(Right.apply)
        else resp.text().map(txt => Left(txt))

      }
  end login

  def register(payload: Payloads.Register): Future[Option[String]] =
    val req = new RequestInit {}
    req.method = HttpMethod.PUT
    req.body = toJsonString(payload)

    exponentialFetch(s"/api/auth/register", req)
      .flatMap { resp =>
        if resp.ok then Future.successful(None)
        else resp.text().map(txt => Some(txt))

      }
  end register

  def create(payload: Payloads.Create, token: Token) =
    val req = new RequestInit {}
    req.method = HttpMethod.POST
    req.body = toJsonString(payload)

    exponentialFetch(s"/api/twots/create", addAuth(req, token))
      .authenticated { resp =>
        if resp.ok then Future.successful(None)
        else resp.text().map(txt => Some(txt))

      }
  end create

  def delete_twot(id: String, token: Token) =
    val req = new RequestInit {}
    req.method = HttpMethod.DELETE

    exponentialFetch(s"/api/twots/$id", addAuth(req, token))
      .authenticated(resp => Future.successful(resp.ok))

  def set_uwotm8(
      payload: Payloads.Uwotm8,
      state: Boolean,
      token: Token
  ) =
    val req = new RequestInit {}
    req.method = if state then HttpMethod.PUT else HttpMethod.DELETE
    req.body = toJsonString(payload)

    exponentialFetch(s"/api/twots/uwotm8", addAuth(req, token))
      .authenticated { resp =>
        if resp.ok then Future.successful(None)
        else resp.text().map(txt => Some(txt))

      }
  end set_uwotm8

  def set_follow(
      payload: Payloads.Follow,
      state: Boolean,
      token: Token
  ) =
    val req = new RequestInit {}
    req.method = if state then HttpMethod.PUT else HttpMethod.DELETE
    req.body = toJsonString(payload)

    exponentialFetch(s"/api/thought_leaders/follow", addAuth(req, token))
      .authenticated { resp =>
        if resp.ok then Future.successful(None)
        else resp.text().map(txt => Some(txt))

      }
  end set_follow

end ApiClient
