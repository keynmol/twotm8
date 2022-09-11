package twotm8
package api

sealed trait ErrorInfo:
  def message: String
object ErrorInfo:
  case class NotFound(message: String = "Not Found") extends ErrorInfo
  case class BadRequest(message: String = "Bad Request") extends ErrorInfo
  case class Unauthorized(message: String = "Unauthorized") extends ErrorInfo
  case class ServerError(message: String = "Something broke yo")
      extends ErrorInfo
