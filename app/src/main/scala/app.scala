package twotm8

import openssl.OpenSSL
import twotm8.db.DB

import scala.scalanative.unsafe.*

class App(db: DB)(using z: Zone, config: Settings):

  export db.{
    get_wall,
    create_twot,
    delete_twot,
    get_twots,
    add_follower,
    delete_follower,
    add_uwotm8,
    delete_uwotm8
  }

  def login(nickname: Nickname, plaintextPassword: Password): Option[Token] =
    db.get_credentials(nickname) match
      case None => None
      case Some(authorId -> hashedPassword) =>
        val List(salt, hash) = hashedPassword.ciphertext.split(":").toList
        val expected =
          plaintextPassword.process(pl => OpenSSL.sha256(salt + ":" + pl))

        Option.when(expected.equalsIgnoreCase(hash))(
          Auth.token(authorId)
        )
  end login

  def get_thought_leader(id: AuthorId): Option[ThoughtLeader] =
    db.get_thought_leader_nickname(id).map { nickname =>
      ThoughtLeader(
        id,
        nickname,
        db.get_following(id),
        db.get_followers(id),
        db.get_twots(id)
      )
    }

  def get_thought_leader(
      nick: Nickname,
      watching: Option[AuthorId]
  ): Option[ThoughtLeader] =
    db.get_thought_leader_id(nick).map { id =>
      ThoughtLeader(
        id,
        nick,
        db.get_following(id),
        db.get_followers(id),
        watching match
          case None          => db.get_twots(id)
          case Some(watcher) => db.get_twots_perspective(id, watcher)
      )
    }

  def register(nickname: Nickname, pass: Password): Option[AuthorId] =
    val salt = scala.util.Random.alphanumeric.take(16).mkString
    val hash = pass.process(pl => OpenSSL.sha256(salt + ":" + pl))
    val saltedHash = HashedPassword(salt + ":" + hash)

    db.register(nickname, saltedHash)
  end register

  def validate(token: JWT): Option[AuthContext] =
    Auth.validate(token)

  def healthCheck(): Health =
    Health(
      dbOk = HealthDB(db.connectionIsOkay())
    )
end App
