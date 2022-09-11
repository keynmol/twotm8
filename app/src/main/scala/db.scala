package twotm8
package db

import roach.Codec
import roach.Database

import java.util.UUID
import scala.scalanative.unsafe.*
import scala.util.Using

extension [X](c: Codec[X])
  private[db] inline def wrap[T](o: OpaqueValue[T, X]): Codec[T] =
    c.bimap(o.apply(_), o.value(_))

trait DB:
  def create_twot(authorId: AuthorId, text: Text): Option[TwotId]
  def delete_twot(authorId: AuthorId, twotId: TwotId): Unit
  def get_twots(authorId: AuthorId): Vector[Twot]
  def get_twots_perspective(
      authorId: AuthorId,
      viewedBy: AuthorId
  ): Vector[Twot]

  def connectionIsOkay(): Boolean

  def get_wall(authorId: AuthorId): Vector[Twot]

  def delete_uwotm8(authorId: AuthorId, twot_id: TwotId): Uwotm8Status
  def add_uwotm8(authorId: AuthorId, twot_id: TwotId): Uwotm8Status

  def add_follower(follower: Follower, leader: AuthorId): Unit
  def delete_follower(follower: Follower, leader: AuthorId): Unit
  def get_followers(leader: AuthorId): Vector[Follower]
  def get_following(leader: AuthorId): Vector[AuthorId]

  def get_credentials(nickname: Nickname): Option[(AuthorId, HashedPassword)]
  def get_thought_leader_nickname(id: AuthorId): Option[Nickname]
  def get_thought_leader_id(id: Nickname): Option[AuthorId]
  def register(nickname: Nickname, pass: HashedPassword): Option[AuthorId]
end DB

object DB:
  def postgres(db: roach.Database)(using Zone): DB = new PostgresDB(db)

  private class PostgresDB(db: Database)(using Zone) extends DB:
    import roach.codecs.*

    def connectionIsOkay() = db.connectionIsOkay

    def get_twots(authorId: AuthorId): Vector[Twot] =
      all(get_twots_prepared, authorId, twotCodec)

    def get_twots_perspective(
        authorId: AuthorId,
        viewedBy: AuthorId
    ): Vector[Twot] =
      all(get_twots_perspective_prepared, authorId -> viewedBy, twotCodec)

    def get_credentials(
        nickname: Nickname
    ): Option[(AuthorId, HashedPassword)] =
      one(
        get_thought_leader_credentials_prepared,
        nickname,
        uuid.wrap(AuthorId) ~ hashedPasswordCodec
      )

    def get_wall(authorId: AuthorId): Vector[Twot] =
      all(get_wall_prepared, authorId, twotCodec)

    def create_twot(authorId: AuthorId, text: Text): Option[TwotId] =
      one(create_twot_prepared, authorId -> text, uuid.wrap(TwotId))

    def delete_twot(authorId: AuthorId, twotId: TwotId): Unit =
      exec(delete_twot_prepared, authorId -> twotId)

    def register(nickname: Nickname, pass: HashedPassword): Option[AuthorId] =
      one(
        register_thought_leader_prepared,
        nickname -> pass,
        uuid.wrap(AuthorId)
      )

    def get_followers(leader: AuthorId): Vector[Follower] =
      all(get_followers_prepared, leader, uuid.wrap(Follower))

    def get_following(leader: AuthorId): Vector[AuthorId] =
      all(get_following_prepared, leader, uuid.wrap(AuthorId))

    def add_follower(follower: Follower, leader: AuthorId): Unit =
      exec(add_follower_prepared, leader -> follower)

    def delete_follower(follower: Follower, leader: AuthorId): Unit =
      exec(delete_follower_prepared, leader -> follower)

    def delete_uwotm8(authorId: AuthorId, twot_id: TwotId): Uwotm8Status =
      exec(delete_uwotm8_prepared, authorId -> twot_id)
      Uwotm8Status.No

    def add_uwotm8(authorId: AuthorId, twot_id: TwotId): Uwotm8Status =
      exec(add_uwotm8_prepared, authorId -> twot_id)
      Uwotm8Status.Yes

    def get_thought_leader_nickname(id: AuthorId): Option[Nickname] =
      one(get_thought_leader_by_id, id, varchar.wrap(Nickname))

    def get_thought_leader_id(id: Nickname): Option[AuthorId] =
      one(get_thought_leader_by_nickname, id, uuid.wrap(AuthorId))

    private def one[T, X](
        prep: roach.Database.Prepared[T],
        value: T,
        codec: Codec[X]
    ): Option[X] =
      Using.resource(prep.execute(value).getOrThrow)(_.readOne(codec))

    private def all[T, X](
        prep: roach.Database.Prepared[T],
        value: T,
        codec: Codec[X]
    ): Vector[X] =
      Using.resource(prep.execute(value).getOrThrow)(_.readAll(codec))

    private def exec[T](
        prep: roach.Database.Prepared[T],
        value: T
    ): Unit =
      Using.resource(prep.execute(value).getOrThrow)(_ => ())

    private val hashedPasswordCodec =
      bpchar.bimap(s => HashedPassword(s), _.ciphertext)

    private val twotCodec =
      (uuid.wrap(TwotId) ~
        uuid.wrap(AuthorId) ~
        varchar.wrap(Nickname) ~
        varchar.wrap(Text) ~
        int4.wrap(Uwotm8Count) ~
        bool.wrap(Uwotm8Status)).as[Twot]

    private lazy val get_twots_prepared =
      db.prepare(
        """
          select 
            t.twot_id, 
            t.author_id, 
            a.nickname, 
            t.content, 
            coalesce(u.uwotm8Count, 0), 
            false 
          from 
            twots t 
            left outer join uwotm8_counts u on t.twot_id = u.twot_id 
            inner join thought_leaders a on t.author_id = a.thought_leader_id 
          where 
            t.author_id = $1 
          order by 
            t.added desc
        """,
        "get_twots",
        uuid.wrap(AuthorId)
      ).getOrThrow

    private lazy val get_twots_perspective_prepared =
      db.prepare(
        """
          select 
            t.twot_id, 
            t.author_id, 
            a.nickname, 
            t.content, 
            coalesce(u.uwotm8Count, 0), 
            CASE WHEN w.author_id IS NULL THEN false ELSE true END 
          from 
            twots t 
              left outer join uwotm8_counts u on t.twot_id = u.twot_id 
              inner join thought_leaders a on t.author_id = a.thought_leader_id 
              left outer join uwotm8s w on t.twot_id = w.twot_id 
                                                    and w.author_id = $2 
          where 
            t.author_id = $1 
          order by 
            t.added desc
        """,
        "get_twots_viewed_by_authed_user",
        uuid.wrap(AuthorId) ~ uuid.wrap(AuthorId)
      ).getOrThrow

    private lazy val get_thought_leader_credentials_prepared =
      db.prepare(
        "select thought_leader_id, salted_hash from thought_leaders where lower(nickname) = lower($1::varchar)",
        "get_thought_leader",
        varchar.wrap(Nickname)
      ).getOrThrow

    private val get_thought_leader_by_id =
      db.prepare(
        "select nickname from thought_leaders where thought_leader_id = $1",
        "get_thought_leader_by_id",
        uuid.wrap(AuthorId)
      ).getOrThrow

    private val get_thought_leader_by_nickname =
      db.prepare(
        "select thought_leader_id from thought_leaders where lower(nickname) = lower($1)",
        "get_thought_leader_by_nickname",
        varchar.wrap(Nickname)
      ).getOrThrow

    private val get_wall_prepared =
      db.prepare(
        """
        select 
          t.twot_id, 
          t.author_id, 
          a.nickname, 
          t.content, 
          coalesce(u.uwotm8Count, 0),
          CASE WHEN w.author_id IS NULL 
            THEN false 
            ELSE true 
          END
        from 
          twots t 
            left outer join uwotm8_counts u on t.twot_id = u.twot_id 
            inner join thought_leaders a on t.author_id = a.thought_leader_id
            left outer join uwotm8s w on t.twot_id = w.twot_id and w.author_id = $1
        where 
          t.author_id in (select distinct leader_id from followers where follower = $1) or 
          t.author_id = $1
        order by t.added desc
        """,
        "get_wall",
        uuid.wrap(AuthorId)
      ).getOrThrow

    private lazy val create_twot_prepared =
      db.prepare(
        """
        insert into twots(twot_id, author_id, content, added) 
                  values (gen_random_uuid(), $1, $2, NOW()) 
                  returning twot_id
                  """,
        "create_twot",
        uuid.wrap(AuthorId) ~ varchar.wrap(Text)
      ).getOrThrow

    private lazy val delete_twot_prepared =
      db.prepare(
        """delete from twots where author_id = $1 and twot_id = $2""",
        "create_twot",
        uuid.wrap(AuthorId) ~ uuid.wrap(TwotId)
      ).getOrThrow

    private lazy val get_followers_prepared =
      db.prepare(
        """
        select follower from followers where leader_id = $1
        """,
        "get_followers",
        uuid.wrap(AuthorId)
      ).getOrThrow

    private lazy val get_following_prepared =
      db.prepare(
        """
        select leader_id from followers where follower = $1
        """,
        "get_following",
        uuid.wrap(AuthorId)
      ).getOrThrow

    private lazy val add_uwotm8_prepared =
      db.prepare(
        """
        insert into uwotm8s(author_id, twot_id) 
                  values ($1, $2)
                  on conflict do nothing
                  returning 'ok'::text
                  """,
        "add_uwotm8",
        uuid.wrap(AuthorId) ~ uuid.wrap(TwotId)
      ).getOrThrow

    private lazy val delete_uwotm8_prepared =
      db.prepare(
        """delete from uwotm8s where author_id = $1 and twot_id = $2""",
        "delete_uwotm8",
        uuid.wrap(AuthorId) ~ uuid.wrap(TwotId)
      ).getOrThrow

    private lazy val add_follower_prepared =
      db.prepare(
        """
        insert into followers(leader_id, follower) 
                  values ($1, $2)
                  on conflict do nothing
                  returning 'ok'::text
                  """,
        "add_follower",
        uuid.wrap(AuthorId) ~ uuid.wrap(Follower)
      ).getOrThrow

    private lazy val delete_follower_prepared =
      db.prepare(
        """delete from followers where leader_id = $1 and follower = $2""",
        "delete_follower",
        uuid.wrap(AuthorId) ~ uuid.wrap(Follower)
      ).getOrThrow

    private lazy val register_thought_leader_prepared =
      db.prepare(
        """
        insert into thought_leaders(thought_leader_id, nickname, salted_hash) 
                  values (gen_random_uuid(), lower($1), $2) 
                  on conflict do nothing
                  returning thought_leader_id
                  """,
        "register_thought_leader",
        varchar.wrap(Nickname) ~
          hashedPasswordCodec
      ).getOrThrow
  end PostgresDB
end DB
