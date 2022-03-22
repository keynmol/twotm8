package twotm8
package frontend

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import com.raquo.waypoint.SplitRender
import org.scalajs.dom

import scala.annotation.targetName
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.scalajs.js
import scala.scalajs.js.JSON

object App:

  def renderPage(using
      router: Router[Page]
  )(using AppState): Signal[HtmlElement] =
    SplitRender[Page, HtmlElement](router.$currentPage)
      .collectStatic(Page.Login)(Login)
      .collectStatic(Page.Logout)(Logout)
      .collectStatic(Page.Wall)(Wall)
      .collectStatic(Page.Register)(Register)
      .collectSignal[Page.Profile](Profile)
      .$view

  def app(using state: AppState) =
    given Router[Page] = Page.router

    val logoutButton = state.$token.map { tok =>
      tok.map { _ =>
        button(
          Styles.navButton,
          "Logout",
          navigateTo(Page.Logout)
        )
      }
    }
    val tokenState: Signal[Option[Token]] =
      state.$token.composeChanges(
        EventStream.merge(
          _,
          EventStream.periodic(30 * 1000).sample(state.$token)
        )
      )

    val authRefresh = Observer { (token: Option[Token]) =>
      token.foreach { tok =>
        ApiClient.me(tok).collect {
          case Left(_) =>
            state.clear()
          case Right(prof) =>
            state.setProfile(
              CachedProfile(id = prof.id, nickname = prof.nickname)
            )
        }
      }
    }

    div(
      Styles.container,
      header(
        Styles.logoHeader,
        h1(Styles.logo, "Twotm8"),
        small(
          Styles.logoSubtitle,
          "Safe space for Thought Leaders to thought lead"
        ),
        div(
          div(
            button(
              Styles.navButton,
              "Home",
              navigateTo(Page.Wall)
            ),
            child.maybe <-- logoutButton
          )
        )
      ),
      child <-- renderPage,
      tokenState --> authRefresh
    )
  end app

  def main(args: Array[String]): Unit =
    given state: AppState = AppState.init

    documentEvents.onDomContentLoaded.foreach { _ =>
      import scalacss.ProdDefaults.*

      val sty = styleTag(Styles.render[String])
      dom.document.querySelector("head").appendChild(sty.ref)

      render(dom.document.getElementById("appContainer"), app)
    }(unsafeWindowOwner)

  end main
end App

extension [T](st: Signal[Option[T]])
  def handled(f: T => Element) = st.map {
    case None    => i("loading")
    case Some(t) => f(t)
  }

def authenticatedOnly(using router: Router[Page], state: AppState) =
  state.$token --> { tok =>
    if tok.isEmpty then redirectTo(Page.Login)
  }

def guestOnly(using router: Router[Page], state: AppState) =
  state.$token --> { tok =>
    if tok.isDefined then redirectTo(Page.Wall)
  }

def redirectTo(pg: Page)(using router: Router[Page]) =
  router.pushState(pg)
