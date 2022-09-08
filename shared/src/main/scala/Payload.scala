package twotm8
package api

object Payload:
  case class Login(nickname: Nickname, password: Password)
  case class Register(nickname: Nickname, password: Password)
  case class Create(text: Text)
  case class Uwotm8(twot_id: TwotId)
  case class Follow(thought_leader: AuthorId)
