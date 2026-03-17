package fmgame.desktop.screens

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import fmgame.desktop.{Assets, FMGame}
import fmgame.shared.domain.{LeagueId, MatchId}

/** Widok ligi (F3.3): tabela (getTable), terminarz (getFixtures), „Rozegraj kolejkę”, „Obejrzyj mecz”. */
class LeagueViewScreen(val game: FMGame, val leagueId: LeagueId) extends Screen {

  private val viewport = new ScreenViewport()
  private val stage = new Stage(viewport)
  private val skin = Assets.getSkin

  private val table = new Table(skin)
  table.setFillParent(true)
  table.top().left()
  table.pad(Assets.padSection)
  val leagueTitle = game.currentUser.flatMap { case (userId, _) =>
    game.gameApi.getLeague(leagueId, userId).toOption.map(l => s"${l.name} (kolejka ${l.currentMatchday}/${l.totalMatchdays})")
  }.getOrElse(leagueId.toString)
  table.add(new Label(leagueTitle, skin.get("title", classOf[Label.LabelStyle]))).padBottom(Assets.padControl).row()
  game.currentUser.foreach { case (userId, _) =>
    game.gameApi.getLeague(leagueId, userId).toOption.filter(l => l.currentMatchday >= (l.totalMatchdays - 2).max(1) && l.currentMatchday <= l.totalMatchdays && l.seasonPhase == "InProgress").foreach { league =>
      table.add(new Label(s"Kolejka ${league.currentMatchday} – decydująca o utrzymanie / awansie", skin)).padBottom(Assets.padSection).row()
    }
  }

  game.currentUser match {
    case Some((userId, _)) =>
      game.gameApi.getTable(leagueId, userId) match {
        case Right(rows) if rows.nonEmpty =>
          table.add(new Label("Tabela", skin)).padBottom(Assets.padControl).row()
          rows.take(5).foreach { row =>
            table.add(new Label(s"${row.position}. ${row.teamName} – ${row.points} pkt", skin)).left().padBottom(2).row()
          }
          if (rows.size > 5) table.add(new Label(s"... i ${rows.size - 5} drużyn", skin)).padBottom(Assets.padControl).row()
        case _ =>
          table.add(new Label("Brak tabeli (stub)", skin)).padBottom(Assets.padControl).row()
      }
      val playBtn = new TextButton("Rozegraj kolejkę", skin)
      playBtn.addListener(new ChangeListener {
        override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
          game.gameApi.playMatchday(leagueId, userId) match {
            case Right(_) => game.setScreen(new MatchdayResultsScreen(game, leagueId))
            case Left(msg) => table.add(new Label(s"Błąd: $msg", skin)).padTop(Assets.padControl).row()
          }
      })
      table.add(playBtn).padTop(Assets.padSection).row()
      val myTeamBtn = new TextButton("Moja drużyna", skin)
      myTeamBtn.addListener(new ChangeListener {
        override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
          game.setScreen(new TeamViewScreen(game, leagueId))
      })
      table.add(myTeamBtn).padTop(Assets.padControl).row()

      game.gameApi.getLeague(leagueId, userId).toOption.filter(l => l.leagueSystemName.nonEmpty && l.currentMatchday >= l.totalMatchdays).foreach { league =>
        val systemName = league.leagueSystemName.get
        val seasonBtn = new TextButton("Zastosuj awans/spadek i rozpocznij nowy sezon", skin)
        seasonBtn.addListener(new ChangeListener {
          override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
            game.gameApi.applyPromotionRelegation(systemName, userId) match {
              case Right(_) =>
                game.gameApi.startNextSeasonForSystem(systemName, userId) match {
                  case Right(_) => game.setScreen(new LeagueViewScreen(game, leagueId))
                  case Left(msg) => table.add(new Label(s"Błąd (nowy sezon): $msg", skin)).padTop(Assets.padControl).row()
                }
              case Left(msg) => table.add(new Label(s"Błąd: $msg", skin)).padTop(Assets.padControl).row()
            }
        })
        table.add(seasonBtn).padTop(Assets.padControl).row()
      }

      game.gameApi.getLeague(leagueId, userId).toOption.filter(l => l.seasonPhase == "Finished" && l.tier.exists(t => t >= 2 && t <= 4)).foreach { league =>
        val mdSemi = league.totalMatchdays + 1
        val mdFinal = league.totalMatchdays + 2
        val fixtures = game.gameApi.getFixtures(leagueId, userId, None, None).toOption.getOrElse(Nil)
        val semiMatches = fixtures.filter(_.matchday == mdSemi)
        val finalMatches = fixtures.filter(_.matchday == mdFinal)
        if (semiMatches.isEmpty) {
          val semiBtn = new TextButton("Przygotuj półfinały baraży", skin)
          semiBtn.addListener(new ChangeListener {
            override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
              game.gameApi.createPlayOffSemiFinals(leagueId, userId) match {
                case Right(_) => game.setScreen(new LeagueViewScreen(game, leagueId))
                case Left(msg) => table.add(new Label(s"Błąd: $msg", skin)).padTop(Assets.padControl).row()
              }
          })
          table.add(semiBtn).padTop(Assets.padControl).row()
        } else if (semiMatches.forall(_.status == "Played") && finalMatches.isEmpty) {
          val finalBtn = new TextButton("Przygotuj finał baraży", skin)
          finalBtn.addListener(new ChangeListener {
            override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
              game.gameApi.createPlayOffFinal(leagueId, userId) match {
                case Right(_) => game.setScreen(new LeagueViewScreen(game, leagueId))
                case Left(msg) => table.add(new Label(s"Błąd: $msg", skin)).padTop(Assets.padControl).row()
              }
          })
          table.add(finalBtn).padTop(Assets.padControl).row()
        }
      }

      game.gameApi.getLeague(leagueId, userId).toOption.filter(_.seasonPhase == "Setup").foreach { league =>
        val teamsInLeague = game.gameApi.listTeams(leagueId, userId).toOption.getOrElse(Nil)
        table.add(new Label(s"Drużyny: ${teamsInLeague.size} / ${league.teamCount}", skin)).left().padTop(Assets.padControl).row()
        val inviteBtn = new TextButton("Zaproś gracza (e-mail)", skin)
        inviteBtn.addListener(new ChangeListener {
          override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
            showInviteDialog(leagueId, userId)
        })
        table.add(inviteBtn).padTop(Assets.padControl).row()
      }

      val teamNames = game.gameApi.listTeams(leagueId, userId).toOption.getOrElse(Nil).map(t => t.id -> t.name).toMap
      def teamName(id: String): String = teamNames.getOrElse(id, id.take(8) + "...")

      table.add(new Label("Terminarz", skin.get("title", classOf[Label.LabelStyle]))).padTop(Assets.padSection).padBottom(Assets.padControl).row()
      game.gameApi.getFixtures(leagueId, userId, Some(20), None) match {
        case Right(fixtures) if fixtures.nonEmpty =>
          fixtures.take(15).foreach { m =>
            val score = (m.homeGoals, m.awayGoals) match {
              case (Some(h), Some(a)) => s" $h : $a"
              case _                  => ""
            }
            val rowLabel = new Label(s"Kolejka ${m.matchday}: ${teamName(m.homeTeamId)} – ${teamName(m.awayTeamId)}$score [${m.status}]", skin)
            table.add(rowLabel).left().padBottom(2).row()
            if (m.status == "Played") {
              val watchBtn = new TextButton("Obejrzyj mecz", skin)
              watchBtn.addListener(new ChangeListener {
                override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                  game.gameApi.getMatchLog(MatchId(m.id), userId, None, None) match {
                    case Right(log) =>
                      game.setPreviousScreen(LeagueViewScreen.this)
                      game.setScreen(new MatchViewScreen(game, MatchId(m.id), leagueId, log))
                    case Left(err) => rowLabel.setText(s"${rowLabel.getText} [błąd: $err]")
                  }
              })
              table.add(watchBtn).left().padBottom(6).row()
            } else {
              val squadBtn = new TextButton("Ustaw skład", skin)
              squadBtn.addListener(new ChangeListener {
                override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit =
                  game.setScreen(new SquadScreen(game, MatchId(m.id), leagueId))
              })
              table.add(squadBtn).left().padBottom(6).row()
            }
          }
        case _ =>
          table.add(new Label("Brak meczów w terminarzu.", skin)).padBottom(Assets.padControl).row()
      }
    case None =>
      table.add(new Label("Zaloguj się.", skin)).row()
  }

  val backBtn = new TextButton("Wstecz do listy lig", skin)
  backBtn.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: com.badlogic.gdx.scenes.scene2d.Actor): Unit = {
      game.setScreen(new LeagueListScreen(game))
    }
  })
  table.add(backBtn).padTop(Assets.padSection).row()

  private def showInviteDialog(leagueId: LeagueId, userId: fmgame.shared.domain.UserId): Unit = {
    val emailField = new TextField("", skin)
    val d = new Dialog("Zaproś gracza", skin) {
      override def result(obj: AnyRef): Unit = {
        if (java.lang.Boolean.TRUE == obj) {
          val email = emailField.getText.trim
          if (email.nonEmpty)
            game.gameApi.createInvitation(leagueId, userId, email) match {
              case Right(_) => showErrorDialog("Zaproszenie", "Wysłano zaproszenie.")
              case Left(msg) => showErrorDialog("Błąd", msg)
            }
        }
        remove()
      }
    }
    d.getContentTable.add(new Label("E-mail zapraszanego gracza:", skin)).row()
    d.getContentTable.add(emailField).width(280f).row()
    d.button("Wyślij", true)
    d.button("Anuluj", false)
    d.show(stage)
  }

  private def showErrorDialog(title: String, msg: String): Unit = {
    val d = new Dialog(title, skin) {
      override def result(obj: AnyRef): Unit = remove()
    }
    d.getContentTable.add(new Label(msg, skin)).width(400f)
    d.button("Zamknij", true)
    d.show(stage)
  }

  val scroll = new ScrollPane(table, skin)
  scroll.setFadeScrollBars(false)
  scroll.setFillParent(true)
  stage.addActor(scroll)

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
