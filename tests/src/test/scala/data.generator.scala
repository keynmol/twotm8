package twotm8
package tests.integration

import cats.effect.*
import cats.effect.std.*
import java.util.UUID

case class Generator private (random: Random[IO], uuid: UUIDGen[IO]):
  def id[T](nt: OpaqueValue[T, UUID]): IO[T] =
    uuid.randomUUID.map(nt.apply(_))

  def int[T](nt: OpaqueValue[T, Int], min: Int, max: Int): IO[T] =
    random.betweenInt(min, max).map(nt.apply(_))

  def string(lengthRange: Range = 0 to 30) =
    for
      length <- random.betweenInt(lengthRange.start, lengthRange.end)
      chars <- random.nextAlphaNumeric.replicateA(length).map(_.mkString)
    yield chars

  def str[T](
      nt: OpaqueValue[T, String],
      lengthRange: Range = 0 to 100
  ): IO[T] =
    for
      chars <- string(lengthRange)
      str = nt.getClass.getSimpleName.toString + "-" + chars
    yield nt(str.take(lengthRange.end))

end Generator

object Generator:
  def resource: Resource[IO, Generator] = Resource
    .eval(Random.scalaUtilRandom[IO])
    .map(_ -> UUIDGen[IO])
    .map(Generator.apply)
