package twotm8

import snunit.*
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import java.util.UUID

trait ApiHelpers:
  inline def handleException(inline handler: Handler): Handler = req =>
    try handler.handleRequest(req)
    catch
      case exc: roach.RoachFatalException =>
        scribe.error(
          s"Failed request at <${req.method.name} ${req.path}> because of postgres, " + "killing the app",
          exc
        )
        req.send(StatusCode.ServiceUnavailable, "", Seq.empty)
        throw exc
      case exc =>
        scribe.error(s"Failed request at <${req.method.name} ${req.path}>", exc)
        req.serverError("Something broke yo")

  extension (r: Request)
    inline def serverError(inline msg: String = "Something broke yo") =
      r.send(StatusCode.InternalServerError, msg, Seq.empty)
  end extension
end ApiHelpers

object ApiHelpers extends ApiHelpers
