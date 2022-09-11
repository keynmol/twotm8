package twotm8
package frontend

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import twotm8.api.Payload

import scala.concurrent.ExecutionContext.Implicits.global

def Register(using router: Router[Page], state: AppState): HtmlElement =
  val error = Var[Option[String]](None)

  div(
    Styles.subContainer,
    h1("Register"),
    guestOnly,
    Flash(error),
    RegisterForm(error),
    p(
      span(Styles.infoText, "Already have an account? "),
      a(
        Styles.infoLink,
        navigateTo(Page.Login),
        "Then go to login page you silly sausage!"
      )
    )
  )
end Register

private def RegisterForm(error: Var[Option[String]])(using
    router: Router[Page],
    state: AppState
) =
  val nickname = Var("")
  val password = Var("")

  val sendRegistration = onClick.preventDefault --> { _ =>
    ApiClient
      .register(
        Payload.Register(
          nickname = Nickname(nickname.now()),
          password = Password(password.now())
        )
      )
      .foreach {
        case e @ Some(err) => error.set(e)
        case None =>
          router.replaceState(Page.Login)

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
        sendRegistration
      )
    )
  )
end RegisterForm
