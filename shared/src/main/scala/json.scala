package twotm8
package json

import sttp.tapir.*
import upickle.default.*

object codecs:
  inline def opaqValue[T, X](obj: OpaqueValue[T, X])(using
      rw: ReadWriter[X]
  ): ReadWriter[T] =
    rw.bimap(obj.value(_), obj.apply(_))

  // primitive types
  given ReadWriter[AuthorId] = opaqValue(AuthorId)
  given ReadWriter[Follower] = opaqValue(Follower)
  given ReadWriter[Nickname] = opaqValue(Nickname)
  given ReadWriter[TwotId] = opaqValue(TwotId)
  given ReadWriter[Text] = opaqValue(Text)
  given ReadWriter[JWT] = opaqValue(JWT)
  given ReadWriter[Uwotm8Count] = opaqValue(Uwotm8Count)
  given ReadWriter[Uwotm8Status] = opaqValue(Uwotm8Status)

  given ReadWriter[Twot] = upickle.default.macroRW[Twot]
  given ReadWriter[Token] = upickle.default.macroRW[Token]
  given ReadWriter[ThoughtLeader] = upickle.default.macroRW[ThoughtLeader]
  // We never use the `Writer` but tapir always needs a `ReadWriter`
  // even if we are only reading
  given ReadWriter[Password] =
    upickle.default.readwriter[String].bimap(_.toString, Password(_))

  // Payloads

  given ReadWriter[api.Payload.Login] =
    upickle.default.macroRW[api.Payload.Login]
  given ReadWriter[api.Payload.Create] =
    upickle.default.macroRW[api.Payload.Create]
  given ReadWriter[api.Payload.Uwotm8] =
    upickle.default.macroRW[api.Payload.Uwotm8]
  given ReadWriter[api.Payload.Register] =
    upickle.default.macroRW[api.Payload.Register]
  given ReadWriter[api.Payload.Follow] =
    upickle.default.macroRW[api.Payload.Follow]

  given Codec.PlainCodec[api.ErrorInfo.NotFound] =
    Codec.string.map(api.ErrorInfo.NotFound(_))(_.message)
  given Codec.PlainCodec[api.ErrorInfo.BadRequest] =
    Codec.string.map(api.ErrorInfo.BadRequest(_))(_.message)
  given Codec.PlainCodec[api.ErrorInfo.Unauthorized] =
    Codec.string.map(api.ErrorInfo.Unauthorized(_))(_.message)
  given Codec.PlainCodec[api.ErrorInfo.ServerError] =
    Codec.string.map(api.ErrorInfo.ServerError(_))(_.message)

  given ReadWriter[HealthDB] = opaqValue(HealthDB)
  given ReadWriter[Health] = upickle.default.macroRW[Health]

  given Schema[HealthDB] = Schema.schemaForBoolean.as[HealthDB]
  given Schema[Health] = Schema.derived
  given Schema[Uwotm8Status] = Schema.schemaForBoolean.as[Uwotm8Status]
  given Schema[Nickname] = Schema.schemaForString.as[Nickname]
  given Schema[Uwotm8Count] = Schema.schemaForInt.as[Uwotm8Count]
  given Schema[Text] = Schema.schemaForString.as[Text]
  given Schema[Follower] = Schema.schemaForUUID.as[Follower]
  given Schema[AuthorId] = Schema.schemaForUUID.as[AuthorId]
  given Schema[TwotId] = Schema.schemaForUUID.as[TwotId]
  given Schema[Twot] = Schema.derived
  given Schema[JWT] = Schema.schemaForString.as[JWT]
  given Schema[Token] = Schema.derived
  given Schema[ThoughtLeader] = Schema.derived

  given Schema[Password] =
    Schema.schemaForString.map(p => Some(Password(p)))(_.toString)
  given Schema[api.Payload.Login] = Schema.derived
  given Schema[api.Payload.Create] = Schema.derived
  given Schema[api.Payload.Uwotm8] = Schema.derived
  given Schema[api.Payload.Register] = Schema.derived
  given Schema[api.Payload.Follow] = Schema.derived

  given Schema[api.ErrorInfo.NotFound] = Schema.derived
  given Schema[api.ErrorInfo.BadRequest] = Schema.derived
  given Schema[api.ErrorInfo.Unauthorized] = Schema.derived
  given Schema[api.ErrorInfo.ServerError] = Schema.derived
  given Schema[api.ErrorInfo] = Schema.derived
end codecs
