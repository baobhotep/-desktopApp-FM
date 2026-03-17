package fmgame.backend.service

import fmgame.backend.domain.*
import fmgame.backend.repository.*
import fmgame.backend.auth.*
import fmgame.shared.domain.*
import fmgame.shared.api.*
import zio.*
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*
import java.time.Instant

trait UserService {
  def register(email: String, password: String, displayName: String): ZIO[Any, String, UserDto]
  def login(email: String, password: String, secret: String): ZIO[Any, String, LoginResponse]
  def getById(userId: UserId): ZIO[Any, String, UserDto]
}

case class UserServiceLive(
  userRepo: UserRepository,
  xa: doobie.Transactor[zio.Task]
) extends UserService {

  private def validateEmail(email: String): ZIO[Any, String, Unit] =
    ZIO.fail("Invalid email address").when(email.trim.isEmpty || !email.contains("@") || !email.contains(".") || email.length > 255).as(())

  def register(email: String, password: String, displayName: String): ZIO[Any, String, UserDto] =
    (for {
      _ <- validateEmail(email)
      _ <- ZIO.fail("Display name must be 1-100 characters").when(displayName.trim.isEmpty || displayName.trim.length > 100)
      _ <- validatePassword(password)
      existing <- userRepo.findByEmail(email).transact(xa).orDie
      _ <- ZIO.fail("Email already registered").when(existing.isDefined)
      id <- IdGen.genUserId
      hash <- AuthService.hashPasswordZIO(password)
      user = User(id, email, hash, displayName, Instant.now())
      _ <- userRepo.create(user).transact(xa).mapError(_ => "Email already registered")
      dto = UserDto(user.id.value, user.email, user.displayName, user.createdAt.toEpochMilli)
      _ <- ZIO.logInfo(s"User registered: ${user.email}")
    } yield dto).tapError(err => ZIO.logWarning(s"Register failed for $email: $err"))

  def login(email: String, password: String, secret: String): ZIO[Any, String, LoginResponse] =
    (for {
      userOpt <- userRepo.findByEmail(email).transact(xa).orDie
      user <- ZIO.fromOption(userOpt).orElseFail("Invalid email or password")
      _ <- ZIO.fail("Invalid email or password").when(!AuthService.verifyPassword(password, user.passwordHash))
      token = AuthService.createToken(user, secret, 86400 * 7)
      dto = UserDto(user.id.value, user.email, user.displayName, user.createdAt.toEpochMilli)
      _ <- ZIO.logInfo(s"User logged in: ${user.email}")
    } yield LoginResponse(token, dto)).tapError(err => ZIO.logWarning(s"Login failed for $email: $err"))

  /** Minimum 8 znaków, co najmniej jedna litera i jedna cyfra. */
  private def validatePassword(password: String): ZIO[Any, String, Unit] = {
    val minLength = 8
    val hasLetter = password.exists(_.isLetter)
    val hasDigit = password.exists(_.isDigit)
    ZIO.fail("Password must be at least 8 characters and contain a letter and a digit")
      .when(password.length < minLength || !hasLetter || !hasDigit)
      .as(())
  }

  def getById(userId: UserId): ZIO[Any, String, UserDto] =
    userRepo.findById(userId).transact(xa).orDie.flatMap {
      case None => ZIO.logWarning(s"User not found: ${userId.value}") *> ZIO.fail("User not found")
      case Some(user) =>
        ZIO.succeed(UserDto(user.id.value, user.email, user.displayName, user.createdAt.toEpochMilli))
    }
}
