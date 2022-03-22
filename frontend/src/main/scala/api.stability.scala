package twotm8
package frontend

import org.scalajs.dom
import org.scalajs.dom.Fetch.fetch
import org.scalajs.dom.*
import org.scalajs.dom.experimental.ResponseInit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.Date

case class Stability(
    initialDelay: FiniteDuration = 100.millis,
    timeout: FiniteDuration = 500.millis,
    delay: FiniteDuration = 100.millis,
    maxRetries: Int = 5,
    retryCodes: Set[Int] = Set(503, 500)
)

def exponentialFetch(
    info: RequestInfo,
    req: RequestInit,
    forceRetry: Boolean = false
)(using stability: Stability): Future[Response] =
  import scalajs.js.Promise
  import stability.*

  type Result = Either[String, Response]

  def go(attemptsRemaining: Int): Promise[Result] =
    val nAttempt = maxRetries - attemptsRemaining
    val newDelay: FiniteDuration =
      if nAttempt == 0 then initialDelay
      else (Math.pow(2.0, nAttempt) * delay.toMillis).millis

    if nAttempt != 0 then
      dom.console.log(
        s"Request to $info will be retried, $attemptsRemaining remaining, with delay $newDelay",
        new Date()
      )

    def sleep(delay: FiniteDuration): Promise[Unit] =
      Promise.apply((resolve, reject) =>
        dom.window.setTimeout(() => resolve(()), delay.toMillis)
      )

    def reqPromise: Promise[Result] =
      fetch(info, req).`then`(resp => Right(resp))

    val retryable =
      forceRetry || req.method.getOrElse(HttpMethod.GET) != HttpMethod.POST

    if (attemptsRemaining == 0) then Promise.resolve(Left("no attempts left"))
    else
      Promise
        .race(js.Array(reqPromise, sleep(timeout).`then`(_ => Left("timeout"))))
        .`then` {
          case Left(reason) =>
            if retryable then
              sleep(newDelay).`then`(_ => go(attemptsRemaining - 1))
            else
              Promise.reject(
                s"Cannot retry the request to $info, reason: $reason"
              )
          case r @ Right(res) =>
            if retryable && retryCodes.contains(res.status) then
              sleep(newDelay).`then`(_ => go(attemptsRemaining - 1))
            else Promise.resolve(r)
        }
    end if
  end go

  go(maxRetries).flatMap {
    case Left(err) =>
      Promise.reject(s"Request to $info failed after all retries: $err")
    case Right(value) =>
      Promise.resolve(value)
  }
end exponentialFetch
