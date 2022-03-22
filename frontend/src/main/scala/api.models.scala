package twotm8
package frontend

import upickle.default.{ReadWriter, Reader, Writer, macroW, macroR}

import scala.scalajs.js
import scala.scalajs.js.JSON

def fromJson[T: Reader](a: js.Any) = upickle.default.read[T](JSON.stringify(a))
def toJsonString[T: Writer](t: T) = upickle.default.write(t)

enum Error:
  case Unauthorized

object Responses:
  case class ThoughtLeaderProfile(
      id: String,
      nickname: String,
      followers: List[String],
      twots: Vector[Twot]
  )
  object ThoughtLeaderProfile:
    given Reader[ThoughtLeaderProfile] = macroR[ThoughtLeaderProfile]

  case class Twot(
      id: String,
      author: String,
      authorNickname: String,
      content: String,
      uwotm8Count: Int,
      uwotm8: Boolean
  )
  object Twot:
    given Reader[Twot] = macroR[Twot]

  case class TokenResponse(jwt: String, expiresIn: Long)
  object TokenResponse:
    given Reader[TokenResponse] = upickle.default.macroR[TokenResponse]
end Responses

object Payloads:

  case class Register(nickname: String, password: String)
  object Register:
    given Writer[Register] = macroW[Register]

  case class Login(nickname: String, password: String)
  object Login:
    given Writer[Login] = macroW[Login]

  case class Create(text: String)
  object Create:
    given Writer[Create] = macroW[Create]

  case class Uwotm8(twot_id: String)
  object Uwotm8:
    given Writer[Uwotm8] = macroW[Uwotm8]

  case class Follow(thought_leader: String)
  object Follow:
    given Writer[Follow] = macroW[Follow]

end Payloads
