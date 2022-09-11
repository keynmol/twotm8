package twotm8
package frontend

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router

import scala.concurrent.ExecutionContext.Implicits.global
import twotm8.api.Payload

def TwotCard(twot: Twot)(using Router[Page], AppState): HtmlElement =
  TwotCard(twot, () => ())

def TwotCard(twot: Twot, update: () => Unit)(using
    Router[Page]
)(using
    state: AppState
): HtmlElement =
  val current = Var(twot.uwotm8)
  import scalajs.js.{Math as JSMath}

  val opacity =
    (1.0 - 0.2 * JSMath.log(1 + twot.uwotm8Count.raw) / JSMath.LOG2E)

  val sendUwotm8 =
    onClick.preventDefault --> { _ =>
      val newState = Uwotm8Status(!current.now().raw)
      state.token.foreach { token =>
        ApiClient
          .set_uwotm8(Payload.Uwotm8(twot.id), newState.raw, token)
          .collect {
            case Right(None)    => current.set(newState)
            case Right(Some(s)) => println("Shit." + s)
          }
      }
    }

  val sendDelete =
    onClick.preventDefault --> { _ =>
      state.token.foreach { token =>
        ApiClient.delete_twot(twot.id, token).foreach { res =>
          update()
        }
      }
    }

  val deleteButton =
    state.$profile.map { prof =>
      prof
        .filter(_.id == twot.author)
        .map(_ => button(Styles.deleteButton, sendDelete, "❌"))
    }

  div(
    Styles.twotCard,
    div(
      Styles.twot,
      styleAttr := s"opacity: $opacity",
      div(
        Styles.twotTitle,
        a(
          Styles.profileLink,
          navigateTo(Page.Profile(twot.authorNickname.raw)),
          "@" + twot.authorNickname
        ),
        child.maybe <-- deleteButton
      ),
      div(
        Styles.twotText,
        twot.content.raw
      )
    ),
    div(
      Styles.twotUwotm8,
      button(
        sendUwotm8,
        cls <-- current.signal.map(v => Styles.uwotm8Button(v.raw).htmlClass),
        "UWOTM8"
      )
    )
  )
end TwotCard
