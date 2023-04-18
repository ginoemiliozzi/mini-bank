package http

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId

object Validation {
  import ValidationFailures._

  trait Required[A] extends (A => Boolean)
  trait MinValue[A] extends ((A, Double) => Boolean)
  trait MinAbs[A] extends ((A, Double) => Boolean)

  implicit val minInt: MinValue[Int] = _ >= _
  implicit val minDouble: MinValue[Double] = _ >= _
  implicit val reqString: Required[String] = _.nonEmpty
  implicit val minAbsDouble: MinAbs[Double] = (value, min) => value.abs >= min

  def required[A](value: A, fieldName: String)(implicit
      req: Required[A]
  ): ValidationResult[A] = {
    if (req(value)) value.validNel
    else EmptyField(fieldName).invalidNel
  }

  def min[A](value: A, minVal: Double, fieldName: String)(implicit
      min: MinValue[A]
  ): ValidationResult[A] = {
    if (min(value, minVal)) value.validNel
    else BelowMinimumValue(fieldName, minVal).invalidNel
  }

  def notNegative[A](value: A, fieldName: String)(implicit
      min: MinValue[A]
  ): ValidationResult[A] = {
    if (min(value, 0)) value.validNel
    else NegativeValue(fieldName).invalidNel
  }

  def minAbs[A](value: A, minValue: Double, fieldName: String)(implicit
      minAbs: MinAbs[A]
  ): ValidationResult[A] = {
    if (minAbs(value, minValue)) value.validNel
    else BelowMinAbsValue(fieldName, minValue).invalidNel
  }

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  trait Validator[A] {
    def validate(value: A): ValidationResult[A]
  }

  def validateEntity[A](payload: A)(implicit
      validator: Validator[A]
  ): ValidationResult[A] =
    validator.validate(payload)
}

object ValidationFailures {
  trait ValidationFailure {
    def errorMessage: String
  }

  case class EmptyField(fieldName: String) extends ValidationFailure {
    override def errorMessage: String = s"Field $fieldName is empty"
  }

  case class NegativeValue(fieldName: String) extends ValidationFailure {
    override def errorMessage: String = s"Field $fieldName is negative"
  }

  case class BelowMinimumValue(fieldName: String, min: Double)
      extends ValidationFailure {
    override def errorMessage: String =
      s"Field $fieldName is below the min $min"
  }

  case class BelowMinAbsValue(fieldName: String, min: Double)
      extends ValidationFailure {
    override def errorMessage: String =
      s"Field $fieldName has an absolute value minimum than $min"
  }
}
