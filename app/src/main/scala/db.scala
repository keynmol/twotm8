package twotm8
package db

import roach.Codec
import roach.Database

import java.util.UUID
import scala.scalanative.unsafe.*
import scala.util.Using
import scala.util.NotGiven

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

  def get_wall(authorId: AuthorId): Vector[Twot]

  def connectionIsOkay(): Boolean

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
  def postgres(db: roach.Pool)(using Zone): DB = new PostgresDB(db)

  private class PostgresDB(db: roach.Pool)(using Zone) extends DB:
    import roach.codecs.*

    override def connectionIsOkay(): Boolean = db.lease(_.connectionIsOkay)

    def get_twots(authorId: AuthorId): Vector[Twot] =
      db.withLease(get_twots_query.all(authorId, twotCodec))

    def get_twots_perspective(
        authorId: AuthorId,
        viewedBy: AuthorId
    ): Vector[Twot] =
      db.withLease(
        get_twots_perspective_query.all(authorId -> viewedBy, twotCodec)
      )

    def get_credentials(
        nickname: Nickname
    ): Option[(AuthorId, HashedPassword)] =
      db.withLease(
        get_thought_leader_credentials_query.one(
          nickname,
          uuid.wrap(AuthorId) ~ hashedPasswordCodec
        )
      )

    def get_wall(authorId: AuthorId): Vector[Twot] =
      db.withLease(get_wall_query.all(authorId, twotCodec))

    def create_twot(authorId: AuthorId, text: Text): Option[TwotId] = 
      db.withLease(create_twot_query.one(authorId -> text, uuid.wrap(TwotId)))

    def delete_twot(authorId: AuthorId, twotId: TwotId): Unit =
      db.withLease(delete_twot_query.exec(authorId -> twotId))

    def register(nickname: Nickname, pass: HashedPassword): Option[AuthorId] =
      db.withLease(
        register_thought_leader_query.one(
          nickname -> pass,
          uuid.wrap(AuthorId)
        )
      )

    def get_followers(leader: AuthorId): Vector[Follower] =
      db.withLease(
        get_following_query.all(leader, uuid.wrap(Follower))
      )

    def get_following(leader: AuthorId): Vector[AuthorId] =
      db.withLease(get_following_query.all(leader, uuid.wrap(AuthorId)))

    def add_follower(follower: Follower, leader: AuthorId): Unit =
      db.withLease(add_follower_query.exec(leader -> follower))

    def delete_follower(follower: Follower, leader: AuthorId): Unit =
      db.withLease(delete_follower_query.exec(leader -> follower))

    def delete_uwotm8(authorId: AuthorId, twot_id: TwotId): Uwotm8Status =
      db.withLease(delete_uwotm8_query.exec(authorId -> twot_id))
      Uwotm8Status.No

    def add_uwotm8(authorId: AuthorId, twot_id: TwotId): Uwotm8Status =
      db.withLease(add_uwotm8_query.exec(authorId -> twot_id))
      Uwotm8Status.Yes

    def get_thought_leader_nickname(id: AuthorId): Option[Nickname] =
      db.withLease(get_thought_leader_by_id.one(id, varchar.wrap(Nickname)))

    def get_thought_leader_id(id: Nickname): Option[AuthorId] =
      db.withLease(get_thought_leader_by_nickname.one(id, uuid.wrap(AuthorId)))

    private def one[T, X](
        prep: roach.Prepared[T],
        value: T,
        codec: Codec[X]
    ): Option[X] =
      Using.resource(prep.execute(value).getOrThrow)(_.readOne(codec))

    private def all[T, X](
        prep: roach.Prepared[T],
        value: T,
        codec: Codec[X]
    ): Vector[X] =
      Using.resource(prep.execute(value).getOrThrow)(_.readAll(codec))

    private def exec[T](
        query: String,
        codec: Codec[T],
        value: T
    ): Unit =
      db.lease(_.executeParams(query, codec, value))

    private val hashedPasswordCodec =
      bpchar.bimap(s => HashedPassword(s), _.ciphertext)

    private val twotCodec =
      (uuid.wrap(TwotId) ~
        uuid.wrap(AuthorId) ~
        varchar.wrap(Nickname) ~
        varchar.wrap(Text) ~
        int4.wrap(Uwotm8Count) ~
        bool.wrap(Uwotm8Status)).as[Twot]

    private val get_twots_query =
      roach
        .Query(
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
          uuid.wrap(AuthorId)
        )

    private val get_twots_perspective_query =
      roach.Query(
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
        uuid.wrap(AuthorId) ~ uuid.wrap(AuthorId)
      )

    private val get_thought_leader_credentials_query =
      roach
        .Query(
          "select thought_leader_id, salted_hash from thought_leaders where lower(nickname) = lower($1::varchar)",
          varchar.wrap(Nickname)
        )

    private val get_thought_leader_by_id =
      roach
        .Query(
          "select nickname from thought_leaders where thought_leader_id = $1",
          uuid.wrap(AuthorId)
        )

    private val get_thought_leader_by_nickname =
      roach
        .Query(
          "select thought_leader_id from thought_leaders where lower(nickname) = lower($1)",
          varchar.wrap(Nickname)
        )

    private val get_wall_query =
      roach
        .Query(
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
          uuid.wrap(AuthorId)
        )

    private val create_twot_query =
      roach
        .Query(
          """
        insert into twots(twot_id, author_id, content, added) 
                  values (gen_random_uuid(), $1, $2, NOW()) 
                  returning twot_id
                  """,
          uuid.wrap(AuthorId) ~ varchar.wrap(Text)
        )

    private val delete_twot_query =
      roach.Query(
        """delete from twots where author_id = $1 and twot_id = $2""",
        uuid.wrap(AuthorId) ~ uuid.wrap(TwotId)
      )

    private val get_followers_query =
      roach.Query(
        """
        select follower from followers where leader_id = $1
        """,
        uuid.wrap(AuthorId)
      )

    private val get_following_query =
      roach
        .Query(
          """
        select leader_id from followers where follower = $1
        """,
          uuid.wrap(AuthorId)
        )

    private val add_uwotm8_query =
      roach.Query(
        """
        insert into uwotm8s(author_id, twot_id)
                  values ($1, $2)
                  on conflict do nothing
                  returning 'ok'::text
                  """,
        uuid.wrap(AuthorId) ~ uuid.wrap(TwotId)
      )

    private val delete_uwotm8_query =
      roach
        .Query(
          """delete from uwotm8s where author_id = $1 and twot_id = $2""",
          uuid.wrap(AuthorId) ~ uuid.wrap(TwotId)
        )

    private val add_follower_query =
      roach
        .Query(
          """
        insert into followers(leader_id, follower) 
                  values ($1, $2)
                  on conflict do nothing
                  returning 'ok'::text
                  """,
          uuid.wrap(AuthorId) ~ uuid.wrap(Follower)
        )

    private val delete_follower_query =
      roach
        .Query(
          """delete from followers where leader_id = $1 and follower = $2""",
          uuid.wrap(AuthorId) ~ uuid.wrap(Follower)
        )

    private val register_thought_leader_query =
      roach
        .Query(
          """
        insert into thought_leaders(thought_leader_id, nickname, salted_hash) 
                  values (gen_random_uuid(), lower($1), $2) 
                  on conflict do nothing
                  returning thought_leader_id
                  """,
          varchar.wrap(Nickname) ~
            hashedPasswordCodec
        )

  end PostgresDB
end DB
