package twotm8

import java.util.UUID

class Secret(val plaintext: String):
  override def toString() = "<secret>"

class Password(plaintext: String):
  override def toString() = "<password>"
  def process[A](f: String => A): A = f(plaintext)

class HashedPassword(val ciphertext: String):
  override def toString() = "<hashed-password>"

case class ThoughtLeader(
    id: AuthorId,
    nickname: Nickname,
    following: Vector[AuthorId],
    followers: Vector[Follower],
    twots: Vector[Twot]
)

case class Health(
    dbOk: Health.DB
):
  def good = dbOk == Health.DB.Yes
object Health:
  opaque type DB = Boolean
  object DB extends YesNo[DB]

opaque type AuthorId = UUID
object AuthorId extends OpaqueValue[AuthorId, UUID]

opaque type Follower = UUID
object Follower extends OpaqueValue[Follower, UUID]

opaque type Nickname = String
object Nickname extends OpaqueValue[Nickname, String]

case class Twot(
    id: TwotId,
    author: AuthorId,
    authorNickname: Nickname,
    content: Text,
    uwotm8Count: Uwotm8Count,
    uwotm8: Uwotm8Status
)

opaque type Uwotm8Status = Boolean
object Uwotm8Status extends YesNo[Uwotm8Status]

opaque type TwotId = UUID
object TwotId extends OpaqueValue[TwotId, UUID]

opaque type Text = String
object Text extends OpaqueValue[Text, String]

opaque type Uwotm8Count = Int
object Uwotm8Count extends OpaqueValue[Uwotm8Count, Int]

case class Token(
    jwt: JWT,
    expiresIn: Long
)

opaque type JWT = String
object JWT extends OpaqueValue[JWT, String]

trait OpaqueValue[T, X](using ap: T =:= X):
  self =>
  inline def apply(s: X): T = ap.flip(s)
  inline def value(t: T): X = ap(t)
  extension (k: T)
    inline def raw = ap(k)
    inline def into[T1](other: OpaqueValue[T1, X]): T1 = other.apply(raw)
    inline def update(inline f: X => X): T =
      apply(f(raw))
end OpaqueValue

trait YesNo[A](using ev: Boolean =:= A) extends OpaqueValue[A, Boolean]:
  val Yes: A = ev.apply(true)
  val No: A = ev.apply(false)
