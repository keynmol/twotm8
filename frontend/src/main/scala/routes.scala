package twotm8
package frontend

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import upickle.default.ReadWriter

sealed trait Page
object Page:
  case object Wall extends Page
  case object Login extends Page
  case object Logout extends Page
  case object Register extends Page
  case class Profile(authorId: String) extends Page

  given ReadWriter[Page.Profile] = upickle.default.macroRW[Page.Profile]
  given ReadWriter[Page] = upickle.default.macroRW[Page]

  val mainRoute = Route.static(Page.Wall, root / endOfSegments)
  val loginRoute = Route.static(Page.Login, root / "login")
  val logoutRoute = Route.static(Page.Logout, root / "logout")
  val registerRoute =
    Route.static(Page.Register, root / "register")

  val profileRoute = Route(
    encode = (stp: Profile) => stp.authorId,
    decode = (arg: String) => Profile(arg),
    pattern = root / "thought_leaders" / segment[String] / endOfSegments
  )

  val router = new Router[Page](
    routes =
      List(mainRoute, loginRoute, registerRoute, profileRoute, logoutRoute),
    getPageTitle = {
      case Wall       => "Twotm8 - safe space for thought leaders"
      case Login      => "Twotm8 - login"
      case Logout     => "Twotm8 - logout"
      case Register   => "Twotm8 - register"
      case Profile(a) => s"Twotm8 - $a"
    },
    serializePage = pg => upickle.default.writeJs(pg).render(),
    deserializePage = str => upickle.default.read[Page](str)
  )(
    $popStateEvent = L.windowEvents.onPopState,
    owner = L.unsafeWindowOwner
  )
end Page

def navigateTo(page: Page)(using router: Router[Page]): Binder[HtmlElement] =
  Binder { el =>
    import org.scalajs.dom

    val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]

    if isLinkElement then el.amend(href(router.absoluteUrlForPage(page)))

    (onClick
      .filter(ev =>
        !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey))
      )
      .preventDefault
      --> (_ => redirectTo(page))).bind(el)
  }
