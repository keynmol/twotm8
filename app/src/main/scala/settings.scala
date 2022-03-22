package twotm8

import scala.concurrent.duration.FiniteDuration

case class Settings(
    tokenExpiration: FiniteDuration,
    secretKey: Secret
)
