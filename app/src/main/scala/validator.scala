package twotm8

def runValidator[T](value: T)(using v: Validator[T]) =
  v.validate(value)

trait Validator[T]:
  self =>
  def validate(value: T): Option[String]

  def contramap[X](f: X => T): Validator[X] =
    new Validator[X]:
      def validate(value: X) = self.validate(f(value))

trait ValidationBuilder[T]:
  self =>
  def failWhen(f: T => Boolean, message: String): ValidationBuilder[T]

  def build: Validator[T]
end ValidationBuilder

object Validator:
  private case class ValidationBuilderImpl[T](funcs: List[Validator[T]])
      extends ValidationBuilder[T]:
    def failWhen(f: T => Boolean, message: String): ValidationBuilder[T] =
      copy(funcs = (t => Option.when(f(t))(message)) :: this.funcs)

    def build: Validator[T] = (t: T) =>
      funcs.collectFirst {
        case v if v.validate(t).isDefined =>
          v.validate(t)
      }.flatten
  end ValidationBuilderImpl

  def builder[T]: ValidationBuilder[T] =
    ValidationBuilderImpl[T](Nil)
end Validator
