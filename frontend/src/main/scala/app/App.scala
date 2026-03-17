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
  val invitationToken: Var[Option[String]] = Var(None)
  /** Current page for logged-in navigation. Replaces tuple-based selectedLeagueId/selectedTeamId/selectedMatchId/lineupContext. */
  val currentPage: Var[Page] = Var(Page.Dashboard)
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
          if (AppState.invitationToken.now().isDefined) AppState.invitationToken.set(None)
          else if (!AppState.tourSeen.now()) {
            AppState.tourSeen.set(true)
            dom.window.localStorage.setItem("fm_tour_seen", "1")
          }
        }
      },
      tabIndex := 0,
      role := "application",
      onMountCallback(ctx => ctx.thisNode.ref.focus()),
      child <-- AppState.token.signal.map {
        case None =>
          div(
            child <-- AppState.currentPage.signal.map {
              case Page.Register => RegisterPage.render
              case _             => LoginPage.render
            }
          )
        case Some(_) =>
          div(
            child <-- AppState.invitationToken.signal.combineWith(AppState.currentPage.signal).map {
              case (Some(tok), _) =>
                AcceptInvitationPage.render(tok, () => AppState.invitationToken.set(None))
              case (None, page) =>
                page match {
                  case Page.Login | Page.Register => DashboardPage.render
                  case Page.Dashboard             => DashboardPage.render
                  case Page.LeagueView(id)        => LeaguePage.render(id, () => AppState.currentPage.set(Page.Dashboard))
                  case Page.TeamView(tid, lid)    => TeamPage.render(tid, () => AppState.currentPage.set(Page.LeagueView(lid)))
                  case Page.MatchView(mid, lid, teamIdOpt) =>
                    MatchDetailPage.render(mid, lid, teamIdOpt, () =>
                      teamIdOpt match {
                        case Some(tid) => AppState.currentPage.set(Page.TeamView(tid, lid))
                        case None      => AppState.currentPage.set(Page.LeagueView(lid))
                      }
                    )
                  case Page.LineupEditor(mid, tid, lid) =>
                    MatchSquadPage.render(mid, tid, () => AppState.currentPage.set(Page.MatchView(mid, lid, Some(tid))))
                  case Page.AcceptInvitation(t) =>
                    AcceptInvitationPage.render(t, () => AppState.currentPage.set(Page.Dashboard))
                }
            }
          )
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
