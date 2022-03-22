package twotm8

import trail.*
import snunit.*
import snunit.routes.*
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

  inline def builder(routes: (Route[?], ArgsHandler[?])*): Handler =
    routes.foldRight[Handler](_.notFound()) { case ((r, a), acc) =>
      RouteHandler(
        r.asInstanceOf[Route[Any]],
        a.asInstanceOf[ArgsHandler[Any]],
        acc
      )
    }

  inline def api(methods: (Method, Handler)*): Handler =
    val mp = methods.toMap

    request =>
      mp.get(request.method) match
        case None => request.notFound()
        case Some(h) =>
          h.handleRequest(request)
  end api

  import upickle.default.{Reader, read}

  inline def json[T: upickle.default.Reader](inline request: Request)(
      inline f: T => Unit
  ) =
    val payload = Try(upickle.default.read[T](request.contentRaw))

    payload match
      case Success(p) => f(p)
      case Failure(ex) =>
        scribe.error(
          s"Error handling JSON request at <${request.method.name}> ${request.path}",
          ex
        )
        request.badRequest("Invalid payload")
  end json

  extension (r: Request)
    inline def sendJson[T: upickle.default.Writer](
        status: StatusCode,
        content: T,
        headers: Map[String, String] = Map.empty
    ) =
      r.send(
        statusCode = status,
        content = upickle.default.writeJs(content).render(),
        headers = headers.updated("Content-type", "application/json").toSeq
      )
    inline def badRequest(
        content: String,
        headers: Map[String, String] = Map.empty
    ) =
      r.send(
        statusCode = StatusCode.BadRequest,
        content = content,
        headers = Seq.empty
      )
    inline def redirect(location: String) =
      r.send(
        StatusCode.TemporaryRedirect,
        content = "",
        headers = Seq("Location" -> location)
      )

    inline def noContent() =
      r.send(StatusCode.NoContent, "", Seq.empty)

    inline def unauthorized(inline msg: String = "Unathorized") =
      r.send(StatusCode.Unauthorized, msg, Seq.empty)

    inline def notFound(inline msg: String = "Not found") =
      r.send(StatusCode.NotFound, msg, Seq.empty)

    inline def serverError(inline msg: String = "Something broke yo") =
      r.send(StatusCode.InternalServerError, msg, Seq.empty)
  end extension

  given Codec[UUID] with
    def encode(t: UUID) = Some(t.toString)
    def decode(v: Option[String]) =
      v match
        case None => None
        case Some(str) =>
          try Some(UUID.fromString(str))
          catch case exc => None

  case class Cookie(
      name: String,
      value: String,
      params: Map[String, Option[String]]
  ):
    def serialize =
      name + "=" + value +
        "; " +
        params.toList
          .sortBy(_._1)
          .map((key, value) => key + value.map("=" + _).getOrElse(""))
          .mkString("; ")
  end Cookie

  object Cookie:
    def read(raw: String): Option[Cookie] =
      try
        val params =
          raw
            .split(";")
            .map(pair =>
              val split = pair.split("=")
              if split.size == 1 then split(0).trim -> Option.empty
              else split(0).trim -> Option(split(1))
            )

        params.headOption.map { case (name, valueO) =>
          Cookie(name, valueO.getOrElse(""), params.tail.toMap)
        }
      catch
        case ex =>
          scribe.error("Failed to parse cookies", ex)
          None
  end Cookie
end ApiHelpers

object ApiHelpers extends ApiHelpers
