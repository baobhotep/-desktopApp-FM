package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant

trait UserRepository {
  def create(user: User): ConnectionIO[Unit]
  def findByEmail(email: String): ConnectionIO[Option[User]]
  def findById(id: UserId): ConnectionIO[Option[User]]
}

object UserRepository {
  def impl: UserRepository = new UserRepository {
    def create(user: User): ConnectionIO[Unit] =
      sql"""
        INSERT INTO users (id, email, password_hash, display_name, created_at)
        VALUES (${user.id.value}, ${user.email}, ${user.passwordHash}, ${user.displayName}, ${user.createdAt})
      """.update.run.map(_ => ())

    def findByEmail(email: String): ConnectionIO[Option[User]] =
      sql"""
        SELECT id, email, password_hash, display_name, created_at
        FROM users WHERE email = $email
      """.query[(String, String, String, String, Instant)].option.map {
        _.map { case (id, email, hash, name, at) =>
          User(UserId(id), email, hash, name, at)
        }
      }

    def findById(id: UserId): ConnectionIO[Option[User]] =
      sql"""
        SELECT id, email, password_hash, display_name, created_at
        FROM users WHERE id = ${id.value}
      """.query[(String, String, String, String, Instant)].option.map {
        _.map { case (id, email, hash, name, at) =>
          User(UserId(id), email, hash, name, at)
        }
      }
  }
}
