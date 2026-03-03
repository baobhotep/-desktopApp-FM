package fmgame.backend.api

import fmgame.backend.TestDbHelper
import fmgame.backend.engine.{EngineConfig, EngineModelFactory, FullMatchEngine}
import fmgame.backend.repository.*
import fmgame.backend.service.*
import fmgame.shared.api.*
import fmgame.shared.domain.*
import zio.*
import zio.test.*
import zio.http.*
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.generic.auto.*
import fmgame.backend.api.MatchSummaryDtoCodec.*

object ApiIntegrationSpec extends ZIOSpecDefault {

  val testDbUrl = "jdbc:h2:mem:api_spec;DB_CLOSE_DELAY=-1"

  private def setupApp(xa: doobie.Transactor[zio.Task]) = for {
    config <- EngineConfig.fromEnvZIO
    initialModels <- EngineModelFactory.fromConfigZIO(config).orDie
    engineModelsRef <- Ref.make(initialModels)
    userSvc = UserServiceLive(UserRepository.impl, xa)
    leagueSvc = LeagueServiceLive(
      LeagueRepository.impl, TeamRepository.impl, UserRepository.impl, InvitationRepository.impl,
      PlayerRepository.impl, RefereeRepository.impl, MatchRepository.impl,
      MatchSquadRepository.impl, MatchResultLogRepository.impl, TransferWindowRepository.impl,
      TransferOfferRepository.impl, LeagueContextRepository.impl, GamePlanSnapshotRepository.impl,
      TrainingPlanRepository.impl, ShortlistRepository.impl, ScoutingReportRepository.impl,
      LeaguePlayerMatchStatsRepository.impl,
      FullMatchEngine, engineModelsRef, xa
    )
    apiRoutes = new ApiRoutes(userSvc, leagueSvc, "test-secret", None)
  } yield apiRoutes

  private def pathFrom(path: String): Path = Path.decode(path)

  private def addAuth(req: Request, token: Option[String]): Request =
    token.fold(req)(t => req.addHeader(Header.Custom("Authorization", s"Bearer $t")))

  private def post[A: io.circe.Decoder](client: Client, routes: ApiRoutes, path: String, body: String, token: Option[String]): ZIO[Scope, Throwable, A] = {
    val req = addAuth(
      Request.post(URL(pathFrom(path)), Body.fromString(body)).addHeader(Header.ContentType(MediaType.application.json)),
      token
    )
    client.request(req).flatMap { resp =>
      resp.body.asString.flatMap(s => ZIO.fromEither(decode[A](s)))
    }
  }

  private def get[A: io.circe.Decoder](client: Client, routes: ApiRoutes, path: String, token: Option[String]): ZIO[Scope, Throwable, A] = {
    val req = addAuth(Request.get(URL(pathFrom(path))), token)
    client.request(req).flatMap { resp =>
      resp.body.asString.flatMap(s => ZIO.fromEither(decode[A](s)))
    }
  }

  private def postStatus(client: Client, routes: ApiRoutes, path: String, body: String, token: Option[String]): ZIO[Scope, Throwable, Status] = {
    val req = addAuth(
      Request.post(URL(pathFrom(path)), Body.fromString(body)).addHeader(Header.ContentType(MediaType.application.json)),
      token
    )
    client.request(req).map(_.status)
  }

  private def getStatus(client: Client, routes: ApiRoutes, path: String, token: Option[String]): ZIO[Scope, Throwable, Status] = {
    val req = addAuth(Request.get(URL(pathFrom(path))), token)
    client.request(req).map(_.status)
  }

  private def toThrowable(e: Any): Throwable = e match {
    case s: String => new RuntimeException(s)
    case t: Throwable => t
  }

  def spec = suite("API integration")(
    test("POST /api/v1/auth/register -> login -> GET /api/v1/auth/me") {
      ZIO.scoped {
        (for {
          runtime <- ZIO.runtime[Any]
          props <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
          xa <- ZIO.succeed {
            implicit val r: zio.Runtime[Any] = runtime
            Transactor.fromDriverManager[zio.Task]("org.h2.Driver", testDbUrl, props, None)
          }
          _ <- TestDbHelper.initSchemaForTest(xa)
          routes <- setupApp(xa)
          _ <- TestClient.addRoutes(routes.app)
          client <- ZIO.service[Client]
          reg <- post[UserDto](client, routes, "/api/v1/auth/register", RegisterRequest("api@test.com", "pass1234", "ApiUser").asJson.noSpaces, None)
          login <- post[LoginResponse](client, routes, "/api/v1/auth/login", LoginRequest("api@test.com", "pass1234").asJson.noSpaces, None)
          me <- get[UserDto](client, routes, "/api/v1/auth/me", Some(login.token))
        } yield assertTrue(
          reg.email == "api@test.com",
          reg.displayName == "ApiUser",
          login.user.email == "api@test.com",
          login.token.nonEmpty,
          me.id == reg.id,
          me.email == reg.email
        )).mapError(toThrowable).orDie
      }.provide(TestClient.layer)
    },
    test("GET /api/v1/leagues/:id returns league after create") {
      ZIO.scoped {
        (for {
          runtime <- ZIO.runtime[Any]
          props <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
          xa <- ZIO.succeed {
            implicit val r: zio.Runtime[Any] = runtime
            Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:api_spec_league;DB_CLOSE_DELAY=-1", props, None)
          }
          _ <- TestDbHelper.initSchemaForTest(xa)
          routes <- setupApp(xa)
          _ <- TestClient.addRoutes(routes.app)
          client <- ZIO.service[Client]
          _ <- post[UserDto](client, routes, "/api/v1/auth/register", RegisterRequest("league@test.com", "pass1234", "User").asJson.noSpaces, None)
          login <- post[LoginResponse](client, routes, "/api/v1/auth/login", LoginRequest("league@test.com", "pass1234").asJson.noSpaces, None)
          createResp <- post[CreateLeagueResponse](client, routes, "/api/v1/leagues", CreateLeagueRequest("Liga Test", 10, "Moja Drużyna", Some("Europe/Warsaw")).asJson.noSpaces, Some(login.token))
          leagueId = createResp.league.id
          getLeague <- get[LeagueDto](client, routes, s"/api/v1/leagues/$leagueId", Some(login.token))
        } yield assertTrue(getLeague.id == leagueId, getLeague.name == "Liga Test", getLeague.teamCount == 10)).mapError(toThrowable).orDie
      }.provide(TestClient.layer)
    },
    test("protected endpoint without token returns 401") {
      ZIO.scoped {
        (for {
          runtime <- ZIO.runtime[Any]
          props <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
          xa <- ZIO.succeed {
            implicit val r: zio.Runtime[Any] = runtime
            Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:api_spec_401;DB_CLOSE_DELAY=-1", props, None)
          }
          _ <- TestDbHelper.initSchemaForTest(xa)
          routes <- setupApp(xa)
          _ <- TestClient.addRoutes(routes.app)
          client <- ZIO.service[Client]
          status <- postStatus(client, routes, "/api/v1/leagues", CreateLeagueRequest("Liga", 10, "Team", Some("Europe/Warsaw")).asJson.noSpaces, None)
        } yield assertTrue(status == Status.Unauthorized)).mapError(toThrowable).orDie
      }.provide(TestClient.layer)
    },
    test("GET /transfer-offers without leagueId returns 400") {
      ZIO.scoped {
        (for {
          runtime <- ZIO.runtime[Any]
          props <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
          xa <- ZIO.succeed {
            implicit val r: zio.Runtime[Any] = runtime
            Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:api_spec_to400;DB_CLOSE_DELAY=-1", props, None)
          }
          _ <- TestDbHelper.initSchemaForTest(xa)
          routes <- setupApp(xa)
          _ <- TestClient.addRoutes(routes.app)
          client <- ZIO.service[Client]
          _ <- post[UserDto](client, routes, "/api/v1/auth/register", RegisterRequest("to400@test.com", "pass1234", "User").asJson.noSpaces, None)
          login <- post[LoginResponse](client, routes, "/api/v1/auth/login", LoginRequest("to400@test.com", "pass1234").asJson.noSpaces, None)
          status <- getStatus(client, routes, "/api/v1/transfer-offers", Some(login.token))
        } yield assertTrue(status == Status.BadRequest)).mapError(toThrowable).orDie
      }.provide(TestClient.layer)
    },
    test("POST /api/v1/auth/register with weak password returns 400") {
      ZIO.scoped {
        (for {
          runtime <- ZIO.runtime[Any]
          props <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
          xa <- ZIO.succeed {
            implicit val r: zio.Runtime[Any] = runtime
            Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:api_spec_weakpw;DB_CLOSE_DELAY=-1", props, None)
          }
          _ <- TestDbHelper.initSchemaForTest(xa)
          routes <- setupApp(xa)
          _ <- TestClient.addRoutes(routes.app)
          client <- ZIO.service[Client]
          status <- postStatus(client, routes, "/api/v1/auth/register", RegisterRequest("weak@test.com", "short", "User").asJson.noSpaces, None)
        } yield assertTrue(status == Status.BadRequest)).mapError(toThrowable).orDie
      }.provide(TestClient.layer)
    },
    test("POST /api/v1/leagues with invalid timezone returns 400") {
      ZIO.scoped {
        (for {
          runtime <- ZIO.runtime[Any]
          props <- ZIO.succeed { val p = new java.util.Properties; p.setProperty("user", "sa"); p.setProperty("password", ""); p }
          xa <- ZIO.succeed {
            implicit val r: zio.Runtime[Any] = runtime
            Transactor.fromDriverManager[zio.Task]("org.h2.Driver", "jdbc:h2:mem:api_spec_tz;DB_CLOSE_DELAY=-1", props, None)
          }
          _ <- TestDbHelper.initSchemaForTest(xa)
          routes <- setupApp(xa)
          _ <- TestClient.addRoutes(routes.app)
          client <- ZIO.service[Client]
          _ <- post[UserDto](client, routes, "/api/v1/auth/register", RegisterRequest("tz@test.com", "pass1234", "User").asJson.noSpaces, None)
          login <- post[LoginResponse](client, routes, "/api/v1/auth/login", LoginRequest("tz@test.com", "pass1234").asJson.noSpaces, None)
          status <- postStatus(client, routes, "/api/v1/leagues", CreateLeagueRequest("Liga", 10, "Team", Some("Invalid/Timezone")).asJson.noSpaces, Some(login.token))
        } yield assertTrue(status == Status.BadRequest)).mapError(toThrowable).orDie
      }.provide(TestClient.layer)
    }
  )
}
