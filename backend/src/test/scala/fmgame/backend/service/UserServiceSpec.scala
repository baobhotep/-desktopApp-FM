package fmgame.backend.service

import fmgame.backend.TestDbHelper
import fmgame.backend.repository.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*

object UserServiceSpec extends ZIOSpecDefault {

  val testDbUrl = "jdbc:h2:mem:test_spec;DB_CLOSE_DELAY=-1"

  def spec = suite("UserService")(
    test("register creates user and login returns token") {
      (for {
        runtime <- ZIO.runtime[Any]
        props   <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
        xa      <- ZIO.succeed {
          implicit val r: zio.Runtime[Any] = runtime
          Transactor.fromDriverManager[zio.Task]("org.h2.Driver", testDbUrl, props, None)
        }
        _       <- TestDbHelper.initSchemaForTest(xa)
        userSvc  = UserServiceLive(UserRepository.impl, xa)
        created <- userSvc.register("test@test.com", "password123", "Test User")
        login   <- userSvc.login("test@test.com", "password123", "secret")
      } yield assertTrue(
        created.email == "test@test.com",
        created.displayName == "Test User",
        login.token.nonEmpty,
        login.user.email == "test@test.com"
      ))
        .mapError { case s: String => new RuntimeException(s); case t: Throwable => t }
        .orDie
        .provide(ZLayer.empty)
    }
  )
}
