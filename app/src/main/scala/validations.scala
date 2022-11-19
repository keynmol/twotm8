package twotm8

given Validator[Password] =
  Validator
    .builder[String]
    .failWhen(
      _.exists(_.isWhitespace),
      "Password cannot contain whitespace symbols"
    )
    .failWhen(
      _.length < 8,
      "Password cannot be shorter than 8 symbols"
    )
    .failWhen(
      _.length > 64,
      "Password cannot be longer than 64 symbols"
    )
    .build
    .contramap[Password](_.process(identity))

given Validator[Nickname] =
  Validator
    .builder[String]
    .failWhen(_.length < 4, "Nickname cannot be shorter than 4 symbols")
    .failWhen(_.length > 32, "Nickname cannot be longer that 32 symbols")
    .failWhen(_.exists(_.isWhitespace), "Nickname cannot have whitespace in it")
    .build
    .contramap[Nickname](_.raw)

given Validator[Text] = Validator
  .builder[String]
  .failWhen(_.trim.isEmpty, "Twot cannot be empty")
  .failWhen(_.trim.length > 256, "Twot cannot longer that 256 characters")
  .build
  .contramap[Text](_.raw)
