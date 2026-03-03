package fmgame.backend.auth

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import com.github.t3hnar.bcrypt._
import pdi.jwt.*
import pdi.jwt.JwtClaim
import io.circe.Json
import io.circe.parser.parse
import java.time.Instant
import zio.*

case class JwtPayload(userId: String, email: String, exp: Long)

object AuthService {
  def hashPassword(password: String): String =
    password.bcryptBounded(12)

  /** Wersja w ZIO: operacja CPU-bound w puli blocking. */
  def hashPasswordZIO(password: String): ZIO[Any, Nothing, String] =
    ZIO.blocking(ZIO.succeed(password.bcryptBounded(12)))

  def verifyPassword(password: String, hash: String): Boolean =
    password.isBcryptedBounded(hash)

  def createToken(user: User, secret: String, expiresInSeconds: Long): String = {
    val now = Instant.now().getEpochSecond
    val payload = Json.obj(
      "userId" -> Json.fromString(user.id.value),
      "email" -> Json.fromString(user.email)
    )
    val claim = JwtClaim(
      content = payload.noSpaces,
      expiration = Some(now + expiresInSeconds),
      issuedAt = Some(now)
    )
    JwtCirce.encode(claim, secret, JwtAlgorithm.HS256)
  }

  def verifyToken(token: String, secret: String): Option[JwtPayload] =
    JwtCirce.decode(token, secret, Seq(JwtAlgorithm.HS256)).toOption.flatMap { claim =>
      parse(claim.content).toOption.flatMap { json =>
        val c = json.hcursor
        for {
          userId <- c.get[String]("userId").toOption
          email <- c.get[String]("email").toOption
          exp <- claim.expiration
        } yield JwtPayload(userId, email, exp)
      }
    }
}
