package twotm8
package api

import roach.RoachException
import roach.RoachFatalException
import snunit.*
import snunit.routes.*
import trail.*

import json.*
import java.util.UUID

object Payload:
  case class Login(nickname: Nickname, password: Password)
  case class Register(nickname: Nickname, password: Password)
  case class Create(text: Text)
  case class Uwotm8(twot_id: TwotId)
  case class Follow(thought_leader: AuthorId)

case class Authenticated[A](auth: AuthContext, value: A)

class Api(app: App):
  import ApiHelpers.{*, given}

  inline def routes = handleException(
    api(
      Method.GET ->
        builder(
          Root / "api" / "thought_leaders" / "me" -> protect(get_me),
          Root / "api" / "thought_leaders" / Arg[String] ->
            optionalAuth(get_thought_leader),
          Root / "api" / "twots" / "wall" -> protect(get_wall),
          Root / "api" / "health" -> get_health
        ),
      Method.POST ->
        builder(
          Root / "api" / "auth" / "login" -> login,
          Root / "api" / "twots" / "create" -> protect(create_twot)
        ),
      Method.PUT ->
        builder(
          Root / "api" / "auth" / "register" -> register,
          Root / "api" / "twots" / "uwotm8" -> protect(add_uwotm8),
          Root / "api" / "thought_leaders" / "follow" -> protect(add_follower)
        ),
      Method.DELETE ->
        builder(
          Root / "api" / "thought_leaders" / "follow" -> protect(
            delete_follower
          ),
          Root / "api" / "twots" / "uwotm8" -> protect(delete_uwotm8),
          Root / "api" / "twots" / Arg[UUID] -> protect(delete_twot)
        )
    )
  )

  inline def extractAuth(request: Request): Either[String, AuthContext] =
    val auth = request.headers.find(_._1.equalsIgnoreCase("Authorization"))

    auth match
      case None => Left("Unauthorized")
      case Some((_, value)) =>
        if !value.startsWith("Bearer ") then Left("Invalid bearer")
        else
          val jwt = JWT(value.drop("Bearer ".length))
          app.validate(jwt) match
            case None =>
              Left(s"Invalid token")
            case Some(auth) =>
              Right(auth)
    end match
  end extractAuth

  inline def optionalAuth[A](
      unprotected: ArgsHandler[Either[A, Authenticated[A]]]
  ): ArgsHandler[A] =
    (request, a) =>
      extractAuth(request) match
        case Left(msg) =>
          unprotected.handleRequest(request, Left(a))
        case Right(auth) =>
          unprotected.handleRequest(request, Right(Authenticated(auth, a)))
  end optionalAuth

  inline def protect[A](
      inline unprotected: ArgsHandler[Authenticated[A]]
  ): ArgsHandler[A] =
    (request, a) =>
      extractAuth(request) match
        case Left(msg) =>
          request.unauthorized(msg)
        case Right(auth) =>
          unprotected.handleRequest(request, Authenticated(auth, a))

  private val add_uwotm8: ArgsHandler[Authenticated[Unit]] = (req, i) =>
    import twotm8.json.codecs.given
    json[Payload.Uwotm8](req) { uwot =>
      req.sendJson(StatusCode.OK, app.add_uwotm8(i.auth.author, uwot.twot_id))
    }
  private val delete_uwotm8: ArgsHandler[Authenticated[Unit]] = (req, i) =>
    import twotm8.json.codecs.given
    json[Payload.Uwotm8](req) { uwot =>
      req.sendJson(
        StatusCode.OK,
        app.delete_uwotm8(i.auth.author, uwot.twot_id)
      )
    }

  private val add_follower: ArgsHandler[Authenticated[Unit]] = (req, i) =>
    import twotm8.json.codecs.given
    json[Payload.Follow](req) { follow =>
      if i.auth.author == follow.thought_leader then
        req.badRequest("You cannot follow yourself")
      else
        app.add_follower(
          follower = i.auth.author.into(Follower),
          leader = follow.thought_leader
        )
        req.noContent()
    }

  private val delete_follower: ArgsHandler[Authenticated[Unit]] = (req, i) =>
    import twotm8.json.codecs.given
    json[Payload.Follow](req) { follow =>
      app.delete_follower(
        follower = i.auth.author.into(Follower),
        leader = follow.thought_leader
      )
      req.noContent()
    }

  private val get_wall: ArgsHandler[Authenticated[Unit]] = (req, i) =>
    import twotm8.json.codecs.given
    val twots = app.get_wall(i.auth.author)
    req.sendJson(StatusCode.OK, twots)

  private val get_me: ArgsHandler[Authenticated[Unit]] = (req, i) =>
    import twotm8.json.codecs.given
    app.get_thought_leader(i.auth.author) match
      case None => req.unauthorized()
      case Some(tl) =>
        req.sendJson(StatusCode.OK, tl)

  private val create_twot: ArgsHandler[Authenticated[Unit]] = (req, i) =>
    import twotm8.json.codecs.given
    json[Payload.Create](req) { createPayload =>
      val text = createPayload.text.update(_.trim)
      if (text.raw.length == 0) then req.badRequest("Twot cannot be empty")
      else if (text.raw.length > 128) then
        req.badRequest(
          s"Twot cannot be longer than 128 characters (you have ${text.raw.length})"
        )
      else
        app.create_twot(i.auth.author, text.update(_.toUpperCase)) match
          case None =>
            req.badRequest("Something went wrong, and it's probably your fault")
          case Some(_) =>
            req.noContent()
      end if
    }

  private val delete_twot: ArgsHandler[Authenticated[UUID]] = (req, uuid) =>
    val authorId = uuid.auth.author
    val twotId = TwotId(uuid.value)

    app.delete_twot(authorId, twotId)
    req.noContent()

  private val get_health: ArgsHandler[Unit] = (req, i) =>
    import twotm8.json.codecs.given
    val health = app.healthCheck()
    if health.good then req.sendJson(StatusCode.OK, health)
    else req.sendJson(StatusCode.InternalServerError, health)

  private val register: ArgsHandler[Unit] = (req, i) =>
    import twotm8.json.codecs.given
    json[Payload.Register](req) { reg =>
      val length = reg.password.process(_.length)
      val hasWhitespace = reg.password.process(_.exists(_.isWhitespace))
      val nicknameHasWhitespace =
        reg.nickname.raw.exists(_.isWhitespace)

      if hasWhitespace then
        req.badRequest("Password cannot contain whitespace symbols")
      else if length == 0 then req.badRequest("Password cannot be empty")
      else if length < 8 then
        req.badRequest("Password cannot be shorter than 8 symbols")
      else if length > 64 then
        req.badRequest("Password cannot be longer than 64 symbols")
      else if reg.nickname.raw.length < 4 then
        req.badRequest("Nickname cannot be shorter than 4 symbols")
      else if reg.nickname.raw.length > 32 then
        req.badRequest("Nickname cannot be longer that 32 symbols")
      else if nicknameHasWhitespace then
        req.badRequest("Nickname cannot have whitespace in it")
      else
        app.register(reg.nickname, reg.password) match
          case None =>
            req.badRequest("This nickname is already taken")
          case Some(_) =>
            req.noContent()
      end if
    }

  private val get_thought_leader
      : ArgsHandler[Either[String, Authenticated[String]]] =
    (req, i) =>
      import twotm8.json.codecs.given
      val nickname = i match
        case Left(name)                    => name
        case Right(Authenticated(_, name)) => name

      val watcher = i.toOption.map(_.auth.author)

      app.get_thought_leader(Nickname(nickname), watcher) match
        case None =>
          req.notFound()
        case Some(tl) =>
          req.sendJson(StatusCode.OK, tl)

  private val login: ArgsHandler[Unit] = (req, _) =>
    import twotm8.json.codecs.given
    json[Payload.Login](req) { login =>
      app.login(login.nickname, login.password) match
        case None      => req.badRequest("Invalid credentials")
        case Some(tok) => req.sendJson(StatusCode.OK, tok)
    }
end Api
