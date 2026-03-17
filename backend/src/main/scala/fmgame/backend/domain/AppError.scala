package fmgame.backend.domain

import fmgame.shared.domain.*

sealed trait AppError {
  def message: String
}

object AppError {
  case class NotFound(entity: String, id: String) extends AppError {
    def message: String = s"$entity not found: $id"
  }
  case class Forbidden(reason: String) extends AppError {
    def message: String = s"Forbidden: $reason"
  }
  case class ValidationError(msg: String) extends AppError {
    def message: String = msg
  }
  case class Conflict(msg: String) extends AppError {
    def message: String = msg
  }
  case class General(msg: String) extends AppError {
    def message: String = msg
  }

  def notFound(entity: String, id: String): AppError = NotFound(entity, id)
  def forbidden(reason: String): AppError = Forbidden(reason)
  def validation(msg: String): AppError = ValidationError(msg)
  def conflict(msg: String): AppError = Conflict(msg)
  def general(msg: String): AppError = General(msg)

  def fromServiceError(err: String): AppError = err match {
    case s if s.startsWith("Forbidden")          => Forbidden(s)
    case s if s.toLowerCase.contains("not found") => NotFound("entity", s)
    case s if s.startsWith("Invalid") || s.startsWith("Cannot") || s.startsWith("Missing") => ValidationError(s)
    case s if s.contains("already") || s.contains("duplicate") => Conflict(s)
    case s => General(s)
  }
}
