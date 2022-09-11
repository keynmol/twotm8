package twotm8
package frontend

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import twotm8.api.Payload

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

def Login(using router: Router[Page], state: AppState): HtmlElement =
  val error = Var[Option[String]](None)
  val input = LoginForm(error)
  val form =
    state.$token.map {
      case None      => Some(input)
      case Some(tok) => None
    }

  div(
    Styles.subContainer,
    h1("Login"),
    guestOnly,
    Flash(error),
    child.maybe <-- form,
    p(
      span(Styles.infoText, "Don't have an account yet? "),
      a(Styles.infoLink, navigateTo(Page.Register), "Register then!")
    )
  )
end Login

private def LoginForm(
    error: Var[Option[String]]
)(using router: Router[Page], state: AppState): HtmlElement =
  val nickname = Var("")
  val password = Var("")

  val sendLogin = onClick.preventDefault --> { _ =>
    ApiClient
      .login(
        Payload.Login(nickname = Nickname(nickname.now()), password = Password(password.now()))
      )
      .foreach {
        case Left(err) =>
          error.set(Some(err.message))
        case Right(response) =>
          val token = Token(response.jwt)
          state.setToken(token)
          redirectTo(Page.Wall)

      }
  }

  form(
    div(
      label(
        Styles.inputLabel,
        Styles.infoText,
        forId := "nickname",
        "Nickname"
      ),
      input(
        Styles.inputNickname,
        tpe := "text",
        idAttr := "nickname",
        onInput.mapToValue --> nickname,
        required := true
      )
    ),
    div(
      label(
        Styles.inputLabel,
        Styles.infoText,
        forId := "password",
        "Password"
      ),
      input(
        Styles.inputPassword,
        tpe := "password",
        idAttr := "password",
        onInput.mapToValue --> password,
        required := true
      )
    ),
    div(
      button(
        Styles.inputLabel,
        "dooooeeeeet",
        sendLogin
      )
    )
  )
end LoginForm
