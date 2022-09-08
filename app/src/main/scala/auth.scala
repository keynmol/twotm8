package twotm8

import scala.scalanative.unsafe.*
import scala.scalanative.libc.*

import scala.util.Using
import java.util.UUID
import openssl.functions.*
import openssl.types.*
import java.time.Instant
import scala.concurrent.duration.*
import scala.scalanative.posix.time
import java.util.Base64
import java.{util as ju}
import scala.util.Try
import openssl.OpenSSL

object Auth:
  def validate(
      jwt: JWT
  )(using z: Zone, settings: Settings): Option[AuthContext] =
    val fragments = jwt.raw.split('.')
    if fragments.size != 3 then None
    else
      val header = fragments(0)
      val payload = fragments(1)
      val signature = fragments(2)
      if header != headersString then None
      else
        val expectedSignature =
          OpenSSL.hmac(
            headersString + "." + payload,
            settings.secretKey.plaintext
          )

        Option
          .when(expectedSignature.equalsIgnoreCase(signature)) {
            try
              val js = ujson.read(dec.decode(payload))
              val id = js.obj("sub").str
              val exp = js.obj("exp").num.toLong

              assert(js.obj("iss").str == "io:twotm8:token")

              val currentTime = System.currentTimeMillis() / 1000

              scribe.info(s"Expiration time: $exp, currentTime: $currentTime")

              if (exp > currentTime) then
                Some(AuthContext(AuthorId(UUID.fromString(id))))
              else None
            catch
              case exc =>
                scribe.error("Processing a JWT threw an exception", exc)
                None
          }
          .flatten
      end if
    end if
  end validate

  val enc = Base64.getUrlEncoder().withoutPadding()
  val dec = Base64.getUrlDecoder()

  val headers =
    ujson.Obj(
      "alg" -> ujson.Str("HS256"),
      "typ" -> ujson.Str("JWT")
    )

  val headersString = new String(
    enc.encodeToString(upickle.default.writeToByteArray(headers))
  )

  def token(authorId: AuthorId)(using Zone)(using
      config: Settings
  ): Token =
    val exp =
      (System.currentTimeMillis / 1000) + config.tokenExpiration.toSeconds

    val content = upickle.default.writeToByteArray(
      ujson.Obj(
        "sub" -> ujson.Str(authorId.raw.toString),
        "exp" -> ujson.Num(exp),
        "iss" -> ujson.Str("io:twotm8:token")
      )
    )

    val payload = new String(enc.encodeToString(content))

    val signature =
      OpenSSL.hmac(headersString + "." + payload, config.secretKey.plaintext)

    Token(JWT(headersString + "." + payload + "." + signature), exp)
  end token

end Auth
