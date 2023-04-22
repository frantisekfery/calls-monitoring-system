package sk.glova.monitoringsystem.http

import cats.data.ValidatedNel
import cats.implicits._

object Validation {

  // field must be present
  trait Required[A] extends (A => Boolean)

  // TC instances
  implicit val requiredString: Required[String] = _.nonEmpty

  // usage
  def required[A](value: A)(implicit req: Required[A]): Boolean = req(value)

  // Validated
  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  // validation failures
  trait ValidationFailure {
    def errorMessage: String
  }

  case class EmptyField(fieldName: String) extends ValidationFailure {
    override def errorMessage = s"$fieldName is empty"
  }

  def validateRequired[A: Required](value: A, fieldName: String): ValidationResult[A] =
    if (required(value)) value.validNel
    else EmptyField(fieldName).invalidNel

  // general TC for requests
  trait Validator[A] {
    def validate(value: A): ValidationResult[A]
  }

  def validateEntity[A](value: A)(implicit validator: Validator[A]): ValidationResult[A] =
    validator.validate(value)
}
