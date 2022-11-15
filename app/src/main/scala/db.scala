package twotm8
package db

import roach.*

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

    def delete_twot(authorId: AuthorId, twot: TwotId): Unit =
      db.withLease(delete_twot_query.exec(authorId -> twot))

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

    val authorId = uuid.wrap(AuthorId)
    val twotId = uuid.wrap(TwotId)
    val nickname = varchar.wrap(Nickname)
    val twotText = varchar.wrap(Text)

    private val twotCodec =
      (twotId ~
        authorId ~
        nickname ~
        twotText ~
        int4.wrap(Uwotm8Count) ~
        bool.wrap(Uwotm8Status)).as[Twot]

    private val get_twots_query =
      sql"""
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
            t.author_id = $authorId
          order by 
            t.added desc
        """

    private val get_twots_perspective_query =
      sql"""
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
                                                    and w.author_id = $authorId
          where 
            t.author_id = $authorId
          order by 
            t.added desc
        """

    private val get_thought_leader_credentials_query =
      sql"""select thought_leader_id, salted_hash 
                from thought_leaders where lower(nickname) = lower($nickname::varchar)"""

    private val get_thought_leader_by_id =
      sql"select nickname from thought_leaders where thought_leader_id = $authorId"

    private val get_thought_leader_by_nickname =
      sql"select thought_leader_id from thought_leaders where lower(nickname) = lower($nickname)"

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
          authorId
        )

    private val create_twot_query =
      sql"""
        insert into twots(twot_id, author_id, content, added) 
                  values (gen_random_uuid(), $authorId, $twotText, NOW()) 
                  returning twot_id
                  """

    private val delete_twot_query =
      sql"""delete from twots where author_id = $authorId and twot_id = $twotId"""

    private val get_followers_query =
      sql"""
        select follower from followers where leader_id = $authorId
        """

    private val get_following_query =
      sql"""
        select leader_id from followers where follower = $authorId
        """

    private val add_uwotm8_query =
      sql"""
        insert into uwotm8s(author_id, twot_id)
                  values (${authorId ~ twotId})
                  on conflict do nothing
                  returning 'ok'::text
                  """

    private val delete_uwotm8_query =
      sql"""delete from uwotm8s where author_id = $authorId and twot_id = $twotId"""

    private val add_follower_query =
      sql"""
        insert into followers(leader_id, follower) 
                  values (${authorId ~ uuid.wrap(Follower)})
                  on conflict do nothing
                  returning 'ok'::text
                  """

    private val delete_follower_query =
      sql"""delete from followers where leader_id = $authorId and follower = ${uuid
          .wrap(Follower)}"""

    private val register_thought_leader_query =
      sql"""
        insert into thought_leaders(thought_leader_id, nickname, salted_hash) 
                  values (gen_random_uuid(), lower($nickname), $hashedPasswordCodec) 
                  on conflict do nothing
                  returning thought_leader_id
                  """
  end PostgresDB
end DB
