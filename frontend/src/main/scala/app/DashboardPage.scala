package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*

object DashboardPage {
  def render: Element = {
    val createLeagueName = Var("")
    val createTeamCount = Var(10)
    val createMyTeamName = Var("")
    val createTimezone = Var("Europe/Warsaw")
    val createError = Var[Option[String]](None)
    val createSuccess = Var[Option[String]](None)
    val busy = Var(false)
    val leagues = Var[List[LeagueDto]](Nil)
    val leaguesError = Var[Option[String]](None)
    val invitationTokenInput = Var("")
    val invitations = Var[List[InvitationDto]](Nil)

    def loadLeagues(): Unit = AppState.token.now() match {
      case None => ()
      case Some(tok) =>
        App.runZio(ApiClient.listLeagues(tok)) {
          case Right(l) => leagues.set(l); leaguesError.set(None)
          case Left(m)  => leaguesError.set(Some(m))
        }
    }
    def loadInvitations(): Unit = AppState.token.now() match {
      case None => ()
      case Some(tok) =>
        App.runZio(ApiClient.listInvitations(tok)) {
          case Right(inv) => invitations.set(inv)
          case Left(_)    => invitations.set(Nil)
        }
    }
    loadLeagues()
    loadInvitations()

    def doLogout(): Unit = {
      org.scalajs.dom.window.localStorage.removeItem("fm-game-jwt")
      AppState.token.set(None)
      AppState.currentUser.set(None)
    }

    def doCreateLeague(): Unit = {
      createError.set(None)
      createSuccess.set(None)
      val tc = createTeamCount.now()
      if (tc < 10 || tc > 20 || tc % 2 != 0) {
        createError.set(Some("Liczba drużyn: 10–20 (parzysta)"))
        return
      }
      if (createLeagueName.now().trim.isEmpty) { createError.set(Some("Podaj nazwę ligi")); return }
      if (createMyTeamName.now().trim.isEmpty) { createError.set(Some("Podaj nazwę swojej drużyny")); return }
      busy.set(true)
      AppState.token.now() match {
        case None => createError.set(Some("Not logged in")); busy.set(false)
        case Some(tok) =>
          App.runZio(ApiClient.createLeague(tok, CreateLeagueRequest(createLeagueName.now().trim, tc, createMyTeamName.now().trim, Some(createTimezone.now())))) {
            case Right(res) =>
              createSuccess.set(Some(s"League '${res.league.name}' created. Your team: ${res.team.name}"))
              createLeagueName.set("")
              createMyTeamName.set("")
              loadLeagues()
              busy.set(false)
            case Left(msg) =>
              createError.set(Some(msg))
              busy.set(false)
          }
      }
    }

    div(
      cls := "max-w-2xl mx-auto",
      div(
        cls := "flex justify-between items-center mb-6",
        child <-- AppState.currentUser.signal.map {
          case Some(u) => span(cls := "font-medium", s"Hello, ${u.displayName}")
          case None    => span(cls := "text-gray-500", "Not loaded")
        },
        button(
          cls := "px-4 py-2 text-sm bg-gray-200 dark:bg-gray-700 rounded hover:bg-gray-300 dark:hover:bg-gray-600",
          "Log out",
          onClick --> { _ => doLogout() }
        )
      ),
      child <-- invitations.signal.map { invList =>
        if (invList.isEmpty) emptyNode
        else div(
          cls := "mb-6 p-4 rounded-lg bg-white dark:bg-gray-800 shadow",
          h2(cls := "text-lg font-bold mb-2", "Zaproszenia do lig"),
          div((Seq(cls := "space-y-2") ++ invList.map { inv =>
            div(
              cls := "flex justify-between items-center p-2 rounded bg-gray-100 dark:bg-gray-700",
              span(cls := "text-sm", s"Liga ${inv.leagueId.take(8)}..."),
              button(
                cls := "px-3 py-1 text-sm bg-indigo-600 text-white rounded hover:bg-indigo-700",
                "Dołącz",
                onClick --> { _ => AppState.invitationToken.set(Some(inv.token)) }
              )
            )
          }): _*)
        )
      },
      div(
        cls := "mb-6 p-4 rounded-lg bg-white dark:bg-gray-800 shadow",
        h2(cls := "text-lg font-bold mb-2", "Akceptuj zaproszenie (token)"),
        p(cls := "text-sm text-gray-500 mb-2", "Masz token z e-maila? Wklej i kliknij Otwórz."),
        div(
          cls := "flex gap-2 mb-2",
          input(
            typ := "text",
            cls := "flex-1 px-3 py-2 border rounded dark:bg-gray-700",
            placeholder := "Token zaproszenia",
            controlled(value <-- invitationTokenInput.signal, onInput.mapToValue --> invitationTokenInput.writer)
          ),
          button(
            cls := "px-4 py-2 bg-indigo-600 text-white rounded hover:bg-indigo-700",
            "Otwórz",
            onClick --> { _ =>
              val t = invitationTokenInput.now().trim
              if (t.nonEmpty) AppState.invitationToken.set(Some(t))
            }
          )
        )
      ),
      div(
        cls := "mb-6 p-4 rounded-lg bg-white dark:bg-gray-800 shadow",
        h2(cls := "text-lg font-bold mb-2", "My leagues"),
        child <-- leaguesError.signal.map {
          case Some(m) => div(cls := "text-red-600 text-sm mb-2", m)
          case None    => emptyNode
        },
        div(
          cls := "space-y-2",
          child <-- leagues.signal.map { list =>
            if (list.isEmpty) div(cls := "text-gray-500 text-sm", "No leagues yet. Create one below.")
            else div(
              list.map { l =>
                button(
                  cls := "w-full text-left px-4 py-2 rounded bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600",
                  s"${l.name} (${l.seasonPhase}, MD ${l.currentMatchday}/${l.totalMatchdays})",
                  onClick --> { _ => AppState.selectedLeagueId.set(Some(l.id)) }
                )
              }: _*
            )
          }
        )
      ),
      div(
        cls := "p-6 rounded-lg shadow bg-white dark:bg-gray-800 mb-6",
        h2(cls := "text-lg font-bold mb-4", "Create league"),
        div(
          cls := "mb-4",
          label(cls := "block text-sm font-medium mb-1", "League name"),
          input(
            typ := "text",
            cls := "w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600",
            placeholder := "My League",
            controlled(value <-- createLeagueName.signal, onInput.mapToValue --> createLeagueName.writer)
          )
        ),
        div(
          cls := "mb-4",
          label(cls := "block text-sm font-medium mb-1", "Liczba drużyn (10–20, parzysta)"),
          input(
            typ := "number",
            cls := "w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600",
            controlled(
              value <-- createTeamCount.signal.map(_.toString),
              onInput.mapToValue.map(s => if (s.isEmpty) 10 else s.toIntOption.getOrElse(10)).map(n => math.max(10, math.min(20, if (n % 2 != 0) n - 1 else n))) --> createTeamCount.writer
            )
          )
        ),
        div(
          cls := "mb-4",
          label(cls := "block text-sm font-medium mb-1", "Strefa czasowa"),
          {
            val tzList = List("Europe/Warsaw", "UTC", "Europe/London", "Europe/Berlin", "America/New_York")
            val opts = tzList.map(tz => option(value := tz, tz))
            val mods = Seq(cls := "w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600", value <-- createTimezone.signal, onChange --> { e => createTimezone.set(e.target.asInstanceOf[org.scalajs.dom.html.Select].value) }) ++ opts
            select(mods: _*)
          }
        ),
        div(
          cls := "mb-4",
          label(cls := "block text-sm font-medium mb-1", "Nazwa twojej drużyny"),
          input(
            typ := "text",
            cls := "w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600",
            placeholder := "My Team",
            controlled(value <-- createMyTeamName.signal, onInput.mapToValue --> createMyTeamName.writer)
          )
        ),
        child <-- createError.signal.map {
          case Some(m) => div(cls := "text-red-600 dark:text-red-400 mb-2 text-sm", m)
          case None    => emptyNode
        },
        child <-- createSuccess.signal.map {
          case Some(m) => div(cls := "text-green-600 dark:text-green-400 mb-2 text-sm", m)
          case None    => emptyNode
        },
        button(
          cls := "w-full py-2 px-4 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50",
          disabled <-- busy.signal,
          "Create league",
          onClick --> { _ => doCreateLeague() }
        )
      )
    )
  }
}
