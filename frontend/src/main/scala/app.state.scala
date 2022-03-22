package twotm8
package frontend

import com.raquo.laminar.api.L.*
import org.scalajs.dom

case class CachedProfile(id: String, nickname: String)

class AppState private (
    _authToken: Var[Option[Token]],
    _profile: Var[Option[CachedProfile]]
):
  def clear() =
    _authToken.set(None)
    _profile.set(None)
    AppState.deleteToken()

  val $token = _authToken.signal
  val $profile = _profile.signal

  def token = _authToken.now()
  def profile = _profile.now()

  def setToken(token: Token) =
    _authToken.set(Some(token))
    AppState.setToken(token.value)

  def setProfile(profile: CachedProfile) =
    _profile.set(Some(profile))

end AppState

object AppState:
  def init: AppState =
    AppState(
      _authToken = Var(getToken()),
      _profile = Var(None)
    )

  private val tokenKey = "twotm8-auth-token"

  private def getToken(): Option[Token] =
    Option(dom.window.localStorage.getItem(tokenKey)).map(Token.apply)

  private def setToken(value: String): Unit =
    Option(dom.window.localStorage.setItem(tokenKey, value))

  private def deleteToken(): Unit =
    dom.window.localStorage.removeItem(tokenKey)

end AppState

case class Token(value: String)
