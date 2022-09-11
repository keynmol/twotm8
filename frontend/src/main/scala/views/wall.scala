package twotm8
package frontend

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router

import scala.scalajs.js.Date
import com.raquo.airstream.core.Signal

def Wall(using router: Router[Page], state: AppState): HtmlElement =

  val welcomeBanner: Signal[Option[HtmlElement]] = state.$profile.map {
    _.map { profile =>
      h1(
        Styles.welcomeBanner,
        "Welcome back, ",
        b(profile.nickname.raw)
      )
    }
  }

  val bus = EventBus[Int]()

  val periodicUpdate = EventStream.periodic(10 * 1000) --> bus.writer

  val reset = () => bus.emit(0)

  val twots: EventStream[Seq[HtmlElement]] =
    bus.events.sample(state.$token).flatMap {
      case None => EventStream.fromValue(Seq.empty)
      case Some(tok) =>
        EventStream
          .fromFuture(ApiClient.get_wall(tok))
          .collect { case Right(twots) =>
            twots.toList.map(TwotCard(_, reset))
          }
    }

  div(
    Styles.twots,
    authenticatedOnly,
    child.maybe <-- welcomeBanner,
    CreateTwot(reset),
    periodicUpdate,
    children <-- twots
  )
end Wall
