package twotm8
package frontend

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router
import japgolly.univeq.UnivEq
import twotm8.api.Payload

import scala.concurrent.ExecutionContext.Implicits.global

import FollowState.*

def Profile(page: Signal[Page.Profile])(using Router[Page])(using
    state: AppState
) =
  val followState = Var(FollowState.Hide)

  val profile: Signal[Option[ThoughtLeader]] =
    page
      .map(_.authorId)
      .combineWith(state.$token)
      .flatMap { case (name, token) =>
        Signal.fromFuture(ApiClient.get_profile(name, token))
      }

  val isFollowing: Signal[FollowState] =
    profile
      .map(_.map(_.followers.toSet).getOrElse(Set.empty))
      .combineWith(state.$profile)
      .map { case (followers, currentUser) =>
        currentUser
          .map { prof =>
            if (followers.exists(follower => follower.raw == prof.id.raw)) then Yes
            else No
          }
          .getOrElse(Hide)
      }

  val renderTwots =
    div(
      Styles.twots,
      children <-- profile.map { p =>
        p.map(_.twots.toList)
          .getOrElse(Nil)
          .map(TwotCard(_))
      }
    )

  val renderName =
    h2(
      Styles.thoughtLeaderHeader,
      child.maybe <-- profile.map(_.map("@" + _.nickname)),
      nbsp,
      child.maybe <-- profile.map { thoughtLeader =>
        thoughtLeader.map { profile =>
          FollowButton(followState, profile)
        }
      }
    )

  div(
    Styles.subContainer,
    isFollowing --> followState,
    renderName,
    renderTwots
  )

end Profile

enum FollowState derives UnivEq:
  case Yes, No, Hide

private def FollowButton(
    followState: Var[FollowState],
    thought_leader: ThoughtLeader
)(using state: AppState) =

  val sendFollow = onClick --> { _ =>
    for
      token <- state.token
      id <- state.profile.map(_.id)
      current = followState.now()
      newState = if current == Yes then No else Yes
    do
      ApiClient
        .set_follow(
          Payload.Follow(thought_leader.id),
          newState == Yes,
          token
        )
        .foreach {
          case Right(None)    => followState.set(newState)
          case Right(Some(s)) => println("Request failed: " + s)
          case Left(e) => println("Request failed, but worse" + e.toString)
        }
  }

  button(
    tpe := "button",
    cls <-- followState.signal.map(Styles.followButton(_)).map(_.htmlClass),
    sendFollow,
    child.text <-- followState.signal.map {
      case Yes       => "Following"
      case No | Hide => "Follow"

    }
  )
end FollowButton
