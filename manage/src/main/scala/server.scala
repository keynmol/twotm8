package twotm8

import scala.util.Using
import scala.scalanative.unsafe.*
import scala.scalanative.libc.*
import scala.util.Try
import java.util.UUID

import roach.*
import openssl.OpenSSL

@main def launch(cmd: String) =
  val postgres = Database.connection_string()

  Zone { implicit z =>
    Using.resource(Database(postgres).getOrThrow) { db =>
      import Codec.*

      cmd match
        case "migrate" =>
          val SCHEMA =
            io.Source
              .fromFile("schema.sql")
              .getLines
              .mkString("\n")
              .split("-----")

          SCHEMA.foreach { s =>
            scribe.info("Executing", s)
            db.execute(s).either match
              case Left(ex)   => scribe.error(ex)
              case Right(res) => scribe.info("OK")

          }
        case "sandbox" =>
          import Codec.*
          import codecs.*

          val x: Codec[(Short, Int, Float, String)] =
            int2 ~ int4 ~ float4 ~ varchar

          case class Howdy(s: UUID, i: Int, str: String)
          val howdyCodec = (uuid ~ int4 ~ text).as[Howdy]

          db.command("DROP TABLE IF EXISTS hello")
          db.command(
            "CREATE TABLE hello(id uuid primary key, test int4, bla text)"
          )
          db.command(
            "insert into hello values (gen_random_uuid(), 25, 'howdyyy');"
          )
          db.command(
            "insert into hello values (gen_random_uuid(), 135, 'test test');"
          )

          Using.resource(db.execute("select * from hello").getOrThrow) { res =>
            res.readAll(howdyCodec).foreach(println)
          }

          val res =
            db.execute("select leader_id, follower, 't'::bool from followers")
              .getOrThrow

          case class FollowRecord(leader_id: UUID, follower: UUID)

          println(uuid ~ uuid ~ bool)

          println(res.readAll(uuid ~ uuid ~ bool))

          val res1 = db
            .executeParams(
              "select id from hello where test = $1 and bla = $2",
              int4 ~ text,
              25 -> "howdyyy"
            )
            .getOrThrow

          println(res1.readAll(uuid))

      end match
    }
  }

end launch
