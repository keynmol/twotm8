package twotm8
package json

import upickle.default.{ReadWriter, Reader}

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
  given Reader[Password] =
    summon[Reader[String]].map(Password(_))

  // Payloads
  import upickle.default.{Reader, macroR}

  given Reader[api.Payload.Login] =
    macroR[api.Payload.Login]
  given Reader[api.Payload.Create] =
    macroR[api.Payload.Create]
  given Reader[api.Payload.Uwotm8] =
    macroR[api.Payload.Uwotm8]
  given Reader[api.Payload.Register] =
    macroR[api.Payload.Register]
  given Reader[api.Payload.Follow] =
    macroR[api.Payload.Follow]
  
  given ReadWriter[Health.DB] = opaqValue(Health.DB)
  given ReadWriter[Health] = upickle.default.macroRW[Health]
end codecs
