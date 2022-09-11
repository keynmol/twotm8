package twotm8
package api

import roach.RoachException
import roach.RoachFatalException
import snunit.tapir.SNUnitInterpreter.*
import sttp.tapir.*
import sttp.tapir.server.*

import java.util.UUID

case class Authenticated[A](auth: AuthContext, value: A)

class Api(app: App):
  import ApiHelpers.{*, given}

  val routes = List(
    endpoints.get_me
      .serverSecurityLogic[AuthContext, Id](validateBearer)
      .serverLogic(get_me),
    endpoints.get_thought_leader
      .serverSecurityLogicSuccess[Either[ErrorInfo, AuthContext], Id] {
        case Some(bearer) => validateBearer(bearer)
        case None         => Left(ErrorInfo.Unauthorized())
      }
      .serverLogic(get_thought_leader),
    endpoints.get_wall
      .serverSecurityLogic[AuthContext, Id](validateBearer)
      .serverLogicSuccess(get_wall),
    endpoints.get_health.serverLogic[Id](get_health),
    endpoints.login.serverLogic[Id](login),
    endpoints.create_twot
      .serverSecurityLogic[AuthContext, Id](validateBearer)
      .serverLogic(create_twot),
    endpoints.register.serverLogic[Id](register),
    endpoints.add_uwotm8
      .serverSecurityLogic[AuthContext, Id](validateBearer)
      .serverLogicSuccess(add_uwotm8),
    endpoints.add_follower
      .serverSecurityLogic[AuthContext, Id](validateBearer)
      .serverLogic(add_follower),
    endpoints.delete_follower
      .serverSecurityLogic[AuthContext, Id](validateBearer)
      .serverLogicSuccess(delete_follower),
    endpoints.delete_uwotm8
      .serverSecurityLogic[AuthContext, Id](validateBearer)
      .serverLogicSuccess(delete_uwotm8),
    endpoints.delete_twot
      .serverSecurityLogic[AuthContext, Id](validateBearer)
      .serverLogicSuccess(delete_twot)
  )

  private def validateBearer(bearer: JWT): Either[ErrorInfo, AuthContext] =
    app.validate(bearer) match
      case None =>
        Left(ErrorInfo.Unauthorized())
      case Some(auth) =>
        Right(auth)
  end validateBearer

  private def add_uwotm8(auth: AuthContext)(uwot: Payload.Uwotm8) =
    app.add_uwotm8(auth.author, uwot.twot_id)

  private def delete_uwotm8(auth: AuthContext)(uwot: Payload.Uwotm8) =
    app.delete_uwotm8(auth.author, uwot.twot_id)

  private def add_follower(auth: AuthContext)(follow: Payload.Follow) =
    if auth.author == follow.thought_leader then
      Left(ErrorInfo.BadRequest("You cannot follow yourself"))
    else
      app.add_follower(
        follower = auth.author.into(Follower),
        leader = follow.thought_leader
      )
      Right(())

  private def delete_follower(auth: AuthContext)(follow: Payload.Follow) =
    app.delete_follower(
      follower = auth.author.into(Follower),
      leader = follow.thought_leader
    )

  private def get_wall(auth: AuthContext)(unit: Unit) =
    val twots = app.get_wall(auth.author)
    twots

  private def get_me(auth: AuthContext)(
      unit: Unit
  ): Either[ErrorInfo, ThoughtLeader] =
    app.get_thought_leader(auth.author) match
      case None     => Left(ErrorInfo.Unauthorized())
      case Some(tl) => Right(tl)

  private def create_twot(auth: AuthContext)(createPayload: Payload.Create) =
    val text = createPayload.text.update(_.trim)
    if (text.raw.length == 0) then
      Left(ErrorInfo.BadRequest("Twot cannot be empty"))
    else if (text.raw.length > 128) then
      Left(
        ErrorInfo.BadRequest(
          s"Twot cannot be longer than 128 characters (you have ${text.raw.length})"
        )
      )
    else
      app.create_twot(auth.author, text.update(_.toUpperCase)) match
        case None =>
          Left(
            ErrorInfo.BadRequest(
              "Something went wrong, and it's probably your fault"
            )
          )
        case Some(_) =>
          Right(())
    end if
  end create_twot

  private def delete_twot(auth: AuthContext)(twotId: TwotId): Unit =
    val authorId = auth.author

    app.delete_twot(authorId, twotId)

  private def get_health(unit: Unit): Either[ErrorInfo, Health] =
    val health = app.healthCheck()
    if health.good then Right(health)
    else Left(ErrorInfo.ServerError())

  private def register(reg: Payload.Register) =
    val length = reg.password.process(_.length)
    val hasWhitespace = reg.password.process(_.exists(_.isWhitespace))
    val nicknameHasWhitespace =
      reg.nickname.raw.exists(_.isWhitespace)

    if hasWhitespace then
      Left(ErrorInfo.BadRequest("Password cannot contain whitespace symbols"))
    else if length == 0 then
      Left(ErrorInfo.BadRequest("Password cannot be empty"))
    else if length < 8 then
      Left(ErrorInfo.BadRequest("Password cannot be shorter than 8 symbols"))
    else if length > 64 then
      Left(ErrorInfo.BadRequest("Password cannot be longer than 64 symbols"))
    else if reg.nickname.raw.length < 4 then
      Left(ErrorInfo.BadRequest("Nickname cannot be shorter than 4 symbols"))
    else if reg.nickname.raw.length > 32 then
      Left(ErrorInfo.BadRequest("Nickname cannot be longer that 32 symbols"))
    else if nicknameHasWhitespace then
      Left(ErrorInfo.BadRequest("Nickname cannot have whitespace in it"))
    else
      app.register(reg.nickname, reg.password) match
        case None =>
          Left(ErrorInfo.BadRequest("This nickname is already taken"))
        case Some(_) =>
          Right(())
    end if
  end register

  private def get_thought_leader(auth: Either[ErrorInfo, AuthContext])(
      nickname: String
  ): Either[ErrorInfo, ThoughtLeader] =
    val watcher = auth.toOption.map(_.author)

    app.get_thought_leader(Nickname(nickname), watcher) match
      case None =>
        Left(ErrorInfo.NotFound())
      case Some(tl) =>
        Right(tl)
  end get_thought_leader

  private def login(login: Payload.Login): Either[ErrorInfo, Token] =
    app.login(login.nickname, login.password) match
      case None      => Left(ErrorInfo.BadRequest("Invalid credentials"))
      case Some(tok) => Right(tok)
end Api
