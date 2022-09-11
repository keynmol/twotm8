package twotm8
package frontend

import com.raquo.laminar.api.L.*
import org.scalajs.dom

case class CachedProfile(id: AuthorId, nickname: Nickname)

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
    Option(dom.window.localStorage.getItem(tokenKey)).map(value =>
      Token(JWT(value))
    )

  private def setToken(value: JWT): Unit =
    Option(dom.window.localStorage.setItem(tokenKey, value.raw))

  private def deleteToken(): Unit =
    dom.window.localStorage.removeItem(tokenKey)

end AppState

case class Token(value: JWT)
