package twotm8

import openssl.functions.RAND_bytes
import java.util.UUID

import scalanative.unsafe.*
import scalanative.unsigned.*

import scalanative.libc.*
import stdio.*

def generateRandomUUID(): UUID =
  val bytes = stackalloc[CUnsignedChar](16)

  assert(RAND_bytes(bytes, 16) == 1)

  bytes(6) = ((bytes(6) & 0x0f.toUByte) | 0x40.toUByte).toUByte
  bytes(8) = ((bytes(8) & 0x3f.toUByte) | 0x80.toUByte).toUByte

  val outputBuffer = stackalloc[CChar](37)
  var hyphens = 0

  for i <- 0 until 16 do
    inline def offset: Long = hyphens + i * 2
    if i == 4 || i == 6 || i == 8 || i == 10 then
      outputBuffer(offset) = '-'.toByte
      hyphens += 1

    stdio.sprintf(outputBuffer + offset, c"%02x", bytes(i))
  outputBuffer(36) = 0.toByte

  val longs = bytes.asInstanceOf[Ptr[Long]]

  scribe.trace(
    s"In byte form: Most significant: ${longs(0)}, least: ${longs(1)}"
  )

  val uuid = UUID.fromString(fromCString(outputBuffer))

  scribe.trace(s"UUID: $uuid")

  scribe.trace(s"In UUID form: Most significant: ${uuid
      .getMostSignificantBits()}, least: ${uuid.getLeastSignificantBits()}")

  uuid
end generateRandomUUID
