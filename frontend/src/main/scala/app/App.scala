package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

/** Global app state: current user and JWT. apiBaseUrl: domyślnie localhost dla dev; w prod ustaw (np. build-time / global JS). */
object AppState {
  val token: Var[Option[String]] = Var(None)
  val currentUser: Var[Option[UserDto]] = Var(None)
  val apiBaseUrl: Var[String] = Var("http://localhost:8080")
  val selectedLeagueId: Var[Option[String]] = Var(None)
  val selectedTeamId: Var[Option[String]] = Var(None)
  val selectedMatchId: Var[Option[String]] = Var(None)
  val invitationToken: Var[Option[String]] = Var(None)
  /** (matchId, teamId) when set show MatchSquadPage */
  val lineupContext: Var[Option[(String, String)]] = Var(None)
  /** Czy użytkownik już widział tour (odczyt z localStorage przy starcie). */
  val tourSeen: Var[Boolean] = Var(Option(dom.window.localStorage.getItem("fm_tour_seen")).contains("1"))
}

object App {
  /** Run a Future that yields Either[String, A] and call the callback with the result (or error string on failure). */
  def runFuture[A](future: scala.concurrent.Future[Either[String, A]])(cb: Either[String, A] => Unit): Unit =
    future.onComplete {
      case scala.util.Success(either) => cb(either)
      case scala.util.Failure(t)      => cb(Left(Option(t.getMessage).getOrElse(t.toString)))
    }
  /** Alias for compatibility with existing runZio call sites (now backed by Future). */
  def runZio[A](future: scala.concurrent.Future[Either[String, A]])(cb: Either[String, A] => Unit): Unit =
    runFuture(future)(cb)

  val rootElement: Element = {
    val showLogin = Var(true)
    val tourStep = Var(0)
    val tourSteps = List(
      "1. Utwórz ligę (Dashboard → Nowa liga).",
      "2. Dodaj boty do ligi i uruchom sezon (Start sezonu).",
      "3. Wybierz swoją drużynę i ustaw skład przed meczem (Skład / formacja).",
      "4. Rozgrywaj kolejki i śledź statystyki (Tabela, król strzelców, raporty meczowe)."
    )
    div(
      cls := "min-h-screen bg-gray-100 dark:bg-gray-900 text-gray-900 dark:text-gray-100 p-4",
      onKeyDown.filter(_.key == "Escape") --> { _ =>
        val target = dom.document.activeElement
        val isInput = target != null && (target.tagName == "INPUT" || target.tagName == "TEXTAREA" || target.tagName == "SELECT")
        if (!isInput) {
          AppState.lineupContext.set(None)
          AppState.selectedMatchId.set(None)
          AppState.selectedTeamId.set(None)
          AppState.selectedLeagueId.set(None)
        }
      },
      tabIndex := 0,
      role := "application",
      child <-- AppState.token.signal
        .combineWith(showLogin.signal)
        .combineWith(AppState.selectedLeagueId.signal)
        .combineWith(AppState.selectedTeamId.signal)
        .combineWith(AppState.invitationToken.signal)
        .combineWith(AppState.selectedMatchId.signal)
        .combineWith(AppState.lineupContext.signal)
        .map { x =>
          val tokenOpt: Option[String] = x._1
          val isLogin: Boolean = x._2
          val leagueIdOpt: Option[String] = x._3
          val teamIdOpt: Option[String] = x._4
          val invitationTokenOpt: Option[String] = x._5
          val selectedMatchIdOpt: Option[String] = x._6
          val lineupContextOpt: Option[(String, String)] = x._7
          tokenOpt match {
            case None => if (isLogin) LoginPage.render(showLogin) else RegisterPage.render(showLogin)
            case Some(_) =>
              if (invitationTokenOpt.nonEmpty)
                AcceptInvitationPage.render(invitationTokenOpt.get, () => AppState.invitationToken.set(None))
              else lineupContextOpt match {
                case Some((mid, tid)) => MatchSquadPage.render(mid, tid, () => AppState.lineupContext.set(None))
                case None =>
                  selectedMatchIdOpt match {
                    case Some(mid) => MatchDetailPage.render(mid, () => AppState.selectedMatchId.set(None))
                    case None =>
                      val pair: (Option[String], Option[String]) = (leagueIdOpt, teamIdOpt)
                      pair match {
                        case (Some(_), Some(tid)) => TeamPage.render(tid, () => AppState.selectedTeamId.set(None))
                        case (Some(id), None)     => LeaguePage.render(id, () => AppState.selectedLeagueId.set(None))
                        case (None, _)            => DashboardPage.render
                      }
                  }
              }
          }
        },
      child <-- (AppState.token.signal.combineWith(AppState.tourSeen.signal)).map { case (tok, seen) =>
        if (tok.nonEmpty && !seen) {
          div(
            cls := "fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4",
            div(
              cls := "bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-md w-full p-4 space-y-3",
              h3(cls := "text-lg font-semibold", "Szybki start"),
              p(cls := "text-sm text-gray-600 dark:text-gray-300", child.text <-- tourStep.signal.map(i => tourSteps.lift(i).getOrElse(""))),
              div(
                cls := "flex justify-between",
                button(
                  cls := "px-3 py-1 text-sm bg-gray-200 dark:bg-gray-600 rounded hover:bg-gray-300",
                  "Pomiń",
                  onClick --> { _ =>
                    AppState.tourSeen.set(true)
                    dom.window.localStorage.setItem("fm_tour_seen", "1")
                  }
                ),
                div(
                  cls := "flex gap-2",
                  button(
                    cls := "px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700",
                    child.text <-- tourStep.signal.map(i => if (i >= tourSteps.size - 1) "Zamknij" else "Dalej"),
                    onClick --> { _ =>
                      val next = tourStep.now() + 1
                      if (next >= tourSteps.size) {
                        AppState.tourSeen.set(true)
                        dom.window.localStorage.setItem("fm_tour_seen", "1")
                        tourStep.set(0)
                      } else tourStep.set(next)
                    }
                  )
                )
              )
            )
          )
        } else emptyNode
      }
    )
  }
}
