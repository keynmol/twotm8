package twotm8
package frontend

import scalacss.internal.LengthUnit.ex
import sttp.capabilities.Effect
import sttp.client3.*
import sttp.model.Method

import scala.concurrent.Future
import scala.concurrent.duration.*

class RetryingBackend[P](
    delegate: SttpBackend[Future, P]
)(using stability: Stability)
    extends DelegateSttpBackend[Future, P](delegate):
  import scala.concurrent.ExecutionContext.Implicits.global

  private given retry.Success[
    (Request[?, ?], Either[Throwable, Response[?]])
  ]((req, res) => !RetryWhen.Default(req, res))

  // The default timer is currently broken in JS
  // https://github.com/softwaremill/odelay/pull/19
  private given odelay.Timer = odelay.js.JsTimer.newTimer

  override def send[T, R >: P & Effect[Future]](
      request: Request[T, R]
  ): Future[Response[T]] =
    retry
      .Backoff(stability.maxRetries, stability.delay)
      .apply {
        delegate
          .send(request)
          .transform(tryResponse =>
            scala.util.Success((request, tryResponse.toEither))
          )
      }
      .flatMap {
        case (_, Right(response)) => Future.successful(response)
        case (_, Left(exception)) => Future.failed(exception)
      }

end RetryingBackend
