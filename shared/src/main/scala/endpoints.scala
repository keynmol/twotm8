package twotm8

import twotm8.api.*
import twotm8.json.codecs.{*, given}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.upickle.*
import scala.util.chaining.*

import java.util.UUID

object endpoints:
  private val baseEndpoint = endpoint.errorOut(
    oneOf[ErrorInfo](
      oneOfVariant(
        statusCode(StatusCode.NotFound).and(plainBody[ErrorInfo.NotFound])
      ),
      oneOfVariant(
        statusCode(StatusCode.BadRequest).and(plainBody[ErrorInfo.BadRequest])
      ),
      oneOfVariant(
        statusCode(StatusCode.Unauthorized)
          .and(plainBody[ErrorInfo.Unauthorized])
      ),
      oneOfVariant(
        statusCode(StatusCode.InternalServerError).and(
          plainBody[ErrorInfo.ServerError]
        )
      )
    )
  ).in("api")

  private val secureEndpoint = baseEndpoint
    .securityIn(auth.bearer[JWT]())

  val get_me = secureEndpoint.get
    .in("thought_leaders" / "me")
    .out(jsonBody[ThoughtLeader])

  val get_thought_leader = baseEndpoint
    .securityIn(auth.bearer[Option[JWT]]())
    .get
    .in("thought_leaders" / path[String])
    .out(jsonBody[ThoughtLeader])

  val get_wall = secureEndpoint.get
    .in("twots" / "wall")
    .out(jsonBody[Vector[Twot]])

  val get_health = baseEndpoint.get
    .in("health")
    .out(jsonBody[Health])

  val login = baseEndpoint.post
    .in("auth" / "login")
    .in(jsonBody[Payload.Login])
    .out(jsonBody[Token])

  val create_twot = secureEndpoint.post
    .in("twots" / "create")
    .in(jsonBody[Payload.Create])
    .out(statusCode(StatusCode.NoContent))

  val register = baseEndpoint.put
    .in("auth" / "register")
    .in(jsonBody[Payload.Register])
    .out(statusCode(StatusCode.NoContent))

  val add_uwotm8 = secureEndpoint.put
    .in("twots" / "uwotm8")
    .in(jsonBody[Payload.Uwotm8])
    .out(jsonBody[Uwotm8Status])

  val add_follower = secureEndpoint.put
    .in("thought_leaders" / "follow")
    .in(jsonBody[Payload.Follow])
    .out(statusCode(StatusCode.NoContent))

  val delete_follower = secureEndpoint.delete
    .in("thought_leaders" / "follow")
    .in(jsonBody[Payload.Follow])
    .out(statusCode(StatusCode.NoContent))

  val delete_uwotm8 = secureEndpoint.delete
    .in("twots" / "uwotm8")
    .in(jsonBody[Payload.Uwotm8])
    .out(jsonBody[Uwotm8Status])

  val delete_twot = secureEndpoint.delete
    .in("twots" / path[TwotId])
    .out(statusCode(StatusCode.NoContent))

end endpoints
