package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.domain.{LeagueId, UserId}

/** Po „Rozegraj kolejkę”: wyniki tej kolejki, minitabela i krótki komentarz (wynik + pozycja). */
class MatchdayResultsScreen(val game: FMGame, val leagueId: LeagueId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(Assets.padSection)

  game.currentUser match {
    case Some((userId, _)) =>
      val leagueOpt = game.gameApi.getLeague(leagueId, userId).toOption
      val justPlayed = leagueOpt.map(_.currentMatchday).getOrElse(1)
      table.add(new Label(s"Wyniki kolejki $justPlayed", skin.get("title", classOf[Label.LabelStyle]))).left().padBottom(Assets.padSection).row()

      val teamNames = game.gameApi.listTeams(leagueId, userId).toOption.getOrElse(Nil).map(t => t.id -> t.name).toMap
      val myTeamId = game.gameApi.listTeams(leagueId, userId).toOption.flatMap(_.find(_.ownerUserId.contains(userId.value))).map(_.id)
      def teamName(id: String): String = teamNames.getOrElse(id, id.take(8) + "...")

      game.gameApi.getFixtures(leagueId, userId, None, None) match {
        case Right(fixtures) =>
          val matchdayFixtures = fixtures.filter(_.matchday == justPlayed).sortBy(_.scheduledAt)
          matchdayFixtures.foreach { m =>
            val score = (m.homeGoals, m.awayGoals) match {
              case (Some(h), Some(a)) => s" $h : $a"
              case _                  => " – "
            }
            table.add(new Label(s"${teamName(m.homeTeamId)} – ${teamName(m.awayTeamId)} $score", skin)).left().padBottom(2).row()
          }
        case Left(_) =>
          table.add(new Label("Brak wyników.", skin)).left().padBottom(Assets.padControl).row()
      }

      table.add(new Label("Tabela", skin.get("title", classOf[Label.LabelStyle]))).left().padTop(Assets.padSection).padBottom(Assets.padControl).row()
      game.gameApi.getTable(leagueId, userId) match {
        case Right(rows) if rows.nonEmpty =>
          rows.take(10).foreach { row =>
            val mark = if (myTeamId.contains(row.teamId)) " ← Ty" else ""
            table.add(new Label(s"${row.position}. ${row.teamName} – ${row.points} pkt$mark", skin)).left().padBottom(2).row()
          }
        case _ =>
          table.add(new Label("Brak tabeli.", skin)).left().padBottom(Assets.padControl).row()
      }

      val comment = buildComment(userId, justPlayed, teamNames, myTeamId)
      table.add(new Label(comment, skin)).left().padTop(Assets.padSection).padBottom(Assets.padControl).row()

    case None =>
      table.add(new Label("Zaloguj się.", skin)).row()
  }

  private def buildComment(userId: UserId, justPlayed: Int, teamNames: Map[String, String], myTeamId: Option[String]): String = {
    val fixturesEither = game.gameApi.getFixtures(leagueId, userId, None, None)
    val tableEither = game.gameApi.getTable(leagueId, userId)
    (myTeamId, fixturesEither, tableEither) match {
      case (Some(tid), Right(fixtures), Right(rows)) =>
        val myMatch = fixtures.filter(_.matchday == justPlayed).find(m => m.homeTeamId == tid || m.awayTeamId == tid)
        val resultStr = myMatch.flatMap { m =>
          (m.homeGoals, m.awayGoals) match {
            case (Some(h), Some(a)) =>
              val (my, opp) = if (m.homeTeamId == tid) (h, a) else (a, h)
              Some(if (my > opp) s"Wygrana $my–$opp" else if (my < opp) s"Przegrana $my–$opp" else s"Remis $my–$opp")
            case _ => None
          }
        }.getOrElse("—")
        val posStr = rows.find(_.teamId == tid).map(r => s"${r.position}. miejsce").getOrElse("")
        val base = if (posStr.nonEmpty) s"$resultStr. $posStr" else resultStr
        val myRow = rows.find(_.teamId == tid)
        val milestoneW = if (resultStr.startsWith("Wygrana") && myRow.exists(_.won == 1)) " 🎉 Pierwsza wygrana w sezonie!" else ""
        val milestoneL = if (resultStr.startsWith("Przegrana") && myRow.exists(_.lost == 1)) " 😔 Pierwsza przegrana w sezonie." else ""
        base + milestoneW + milestoneL
      case _ => "Podsumowanie niedostępne."
    }
  }

  val nextBtn = new TextButton("Dalej", skin)
  nextBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
      game.setScreen(new LeagueViewScreen(game, leagueId))
  })
  table.add(nextBtn).left().padTop(Assets.padSection).row()

  stage.addActor(table)

  override def show(): Unit = Gdx.input.setInputProcessor(stage)
  override def render(delta: Float): Unit = {
    Gdx.gl.glClearColor(Assets.screenBackgroundColor.r, Assets.screenBackgroundColor.g, Assets.screenBackgroundColor.b, 1f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    stage.act(delta)
    stage.draw()
  }
  override def resize(width: Int, height: Int): Unit = viewport.update(width, height, true)
  override def pause(): Unit = ()
  override def resume(): Unit = ()
  override def hide(): Unit = ()
  override def dispose(): Unit = stage.dispose()
}
