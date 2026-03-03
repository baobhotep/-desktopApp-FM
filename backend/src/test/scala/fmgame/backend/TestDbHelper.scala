package fmgame.backend

import doobie.*
import doobie.implicits.*
import zio.*
import fmgame.backend.repository.Database

object TestDbHelper {
  /** Inicjalizuje schemat bazy dla testów. Transactor[Task] z runtime w implicit scope. */
  def initSchemaForTest(xa: Transactor[zio.Task]): ZIO[Any, Throwable, Unit] =
    Database.initSchema(xa)
}
