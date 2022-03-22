package twotm8
package frontend

import com.raquo.laminar.api.L.*

def Flash(error: Var[Option[String]]) =
  child.maybe <-- error.signal.map {
    _.map(msg => div(Styles.errorFlash, msg))
  }
