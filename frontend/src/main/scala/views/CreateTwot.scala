package twotm8
package frontend

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.Date

import ApiClient.*
import twotm8.api.Payload

def CreateTwot(
    update: () => Unit
)(using router: Router[Page], state: AppState) =
  val error = Var[Option[String]](None)

  val text = Var("")

  val sendTwot = onClick.preventDefault --> { _ =>
    state.token.foreach { token =>
      ApiClient
        .create(Payload.Create(Text(text.now())), token)
        .collect {
          case Right(e @ Some(err)) => error.set(e)
          case Right(None) =>
            update()
        }
    }
  }
  div(
    Styles.subContainer,
    Flash(error),
    authenticatedOnly,
    div(
      Styles.createForm,
      div(
        Styles.twotInputBlock,
        input(
          Styles.inputTwot,
          tpe := "text",
          onInput.mapToValue --> text
        )
      ),
      div(
        Styles.rageButtonBlock,
        button(
          Styles.rageButton,
          "RAGE",
          sendTwot
        )
      )
    )
  )
end CreateTwot
