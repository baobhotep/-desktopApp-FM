package fmgame.backend.auth

import fmgame.backend.domain.*
import fmgame.shared.domain.UserId
import zio.*
import zio.test.*
import java.time.Instant

object AuthServiceSpec extends ZIOSpecDefault {

  val testUser = User(
    id = UserId("user-1"),
    email = "auth@test.com",
    passwordHash = "",
    displayName = "Auth Test",
    createdAt = Instant.now()
  )

  def spec = suite("AuthService")(
    test("hashPassword and verifyPassword roundtrip") {
      val password = "securePassword123"
      val hash = AuthService.hashPassword(password)
      assertTrue(
        hash != password,
        hash.nonEmpty,
        AuthService.verifyPassword(password, hash),
        !AuthService.verifyPassword("wrong", hash)
      )
    },
    test("createToken and verifyToken roundtrip") {
      val user = testUser.copy(passwordHash = "ignored")
      val secret = "test-secret"
      val token = AuthService.createToken(user, secret, 3600)
      assertTrue(token.nonEmpty)
      val payload = AuthService.verifyToken(token, secret)
      assertTrue(
        payload.isDefined,
        payload.get.userId == user.id.value,
        payload.get.email == user.email
      )
    },
    test("verifyToken with wrong secret returns None") {
      val user = testUser.copy(passwordHash = "x")
      val token = AuthService.createToken(user, "right-secret", 3600)
      val wrong = AuthService.verifyToken(token, "wrong-secret")
      assertTrue(wrong.isEmpty)
    },
    test("verifyToken with malformed token returns None") {
      assertTrue(
        AuthService.verifyToken("not.a.token", "secret").isEmpty,
        AuthService.verifyToken("", "secret").isEmpty
      )
    }
  )
}
