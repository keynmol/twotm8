package twotm8
package tests.integration

import sttp.tapir.*
import sttp.tapir.client.http4s.Http4sClientInterpreter

import org.http4s.client.*
import org.http4s.*
import org.http4s.ember.client.*
import cats.effect.*
import cats.syntax.all.*

enum TestGroup:
  case None
  case Nested(s: Vector[String])

  def testName(tn: weaver.TestName): weaver.TestName =
    this match
      case None      => tn
      case Nested(s) => tn.copy(name = (s.mkString("", ".", ": ") + tn.name))

  def nest(nm: String) =
    this match
      case None      => Nested(Vector(nm))
      case Nested(s) => Nested(s :+ nm)
end TestGroup

abstract class BaseTest extends weaver.IOSuite:
  type Res = (Client[IO], Uri, Generator)

  case class Probe(execute: Execute, generator: Generator)

  type Execute =
    [I, E, O, R] => (PublicEndpoint[I, E, O, R], I) => IO[Either[E, O]]

  val interp = Http4sClientInterpreter[IO]()

  def group(nm: String)(using tg: TestGroup = TestGroup.None)(
      f: TestGroup ?=> Unit
  ) =
    f(using tg.nest(nm))

  def integrationTest(tn: weaver.TestName)(
      f: Probe => IO[weaver.Expectations]
  )(using tg: TestGroup = TestGroup.None) =
    test(tg.testName(tn)) { case (client, uri, generator) =>
      val e: Execute = [I, E, O, R] =>
        (endpoint: PublicEndpoint[I, E, O, R], input: I) =>
          val (req, resp) =
            interp
              .toRequestThrowDecodeFailures(endpoint, baseUri = Some(uri))
              .apply(input)
          client.run(req).use(resp)

      f(Probe(e, generator))
    }

  def checkURL =
    Resource
      .eval(
        IO.fromOption(sys.env.get("TWOTM8_URL"))(
          new RuntimeException(
            "To run integration tests, please set TWOTM8_URL,\n like this: TWOTM8_URL=http://localhost:8080"
          )
        )
      )
      .evalMap(r => IO.fromEither(Uri.fromString(r)))

  def sharedResource = EmberClientBuilder
    .default[IO]
    .build
    .product(checkURL)
    .flatMap((c, u) => Generator.resource.map((c, u, _)))
end BaseTest
