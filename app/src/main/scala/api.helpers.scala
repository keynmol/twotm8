package twotm8

import snunit.*

import java.util.UUID
import scala.util.Failure
import scala.util.Success
import scala.util.Try

trait ApiHelpers:
  extension (r: Request)
    inline def serverError(inline msg: String = "Something broke yo") =
      r.send(StatusCode.InternalServerError, msg, Seq.empty)
  end extension
end ApiHelpers

object ApiHelpers extends ApiHelpers
