package twotm8

import org.junit.Assert.*
import org.junit.Test

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

import ApiHelpers.*
import verify.*
import snunit.Request
import snunit.Method
import scala.collection.mutable
import snunit.StatusCode
import java.util.UUID

case class Response(
    code: StatusCode,
    content: String,
    headers: Seq[(String, String)]
)

case class SimpleRequest(
    path: String = "/",
    method: Method = Method.GET,
    query: String = "",
    headers: Seq[(String, String)] = Seq.empty,
    contentRaw: Array[Byte] = Array.emptyByteArray
)(using into: mutable.Map[SimpleRequest, Response])
    extends Request:
  override def send(
      statusCode: StatusCode,
      content: Array[Byte],
      headers: Seq[(String, String)]
  ) =
    into.update(this, Response(statusCode, new String(content), headers))
end SimpleRequest

object TestApiHelpers extends verify.BasicTestSuite:
  test("route builder") {
    import snunit.routes.*
    import trail.*

    val composed = builder(
      Root / "hello" / "world" / Arg[String] -> { (req: Request, arg: String) =>
        req.send(StatusCode.OK, s"${arg == "bla"}", Seq.empty)
      },
      (Root / "hello" / "test" / Arg[String] / Arg[Int]) -> {
        (req: Request, arg: (String, Int)) =>
          req.send(
            StatusCode.OK,
            s"${arg._1 == "bla"} && ${arg._2 == 25}",
            Seq.empty
          )
      }
    )

    given reg: mutable.Map[SimpleRequest, Response] = mutable.Map.empty

    val req1 = SimpleRequest("/hello/world/bla")
    val req2 = SimpleRequest("/hello/test/bla/25")

    composed.handleRequest(req1)
    composed.handleRequest(req2)

    assert(reg(req1).content == "true")
    assert(reg(req2).content == "true && true")

  }

  test("cookie roundtrip") {
    val cookie = "id=hello; HttpOnly; Max-Age=2592000; SameSite=strict"

    val parsed = Cookie.read(cookie).get
    val expected = Cookie(
      "id",
      "hello",
      Map(
        "Max-Age" -> Some("2592000"),
        "SameSite" -> Some("strict"),
        "HttpOnly" -> None
      )
    )

    assert(parsed == expected)
    assert(expected.serialize == cookie)

    val noAttributesCookie = "test=world"

    val read = Cookie.read(noAttributesCookie).get

    assert(read.name == "test")
    assert(read.value == "world")
  }

end TestApiHelpers
