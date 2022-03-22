package twotm8
package frontend

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.Router

def Logout(using Router[Page])(using state: AppState) =
  div(
    child.maybe <-- Signal.fromValue {
      state.clear()
      redirectTo(Page.Login)
      None
    }
  )
