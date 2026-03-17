package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*

object AcceptInvitationPage {
  def render(tokenFromUrl: String, goBack: () => Unit): Element = {
    val token = Var(tokenFromUrl)
    val teamName = Var("")
    val error = Var[Option[String]](None)
    val success = Var[Option[String]](None)
    val busy = Var(false)

    def submit(): Unit = AppState.token.now() match {
      case None => error.set(Some("Zaloguj się"))
      case Some(tok) =>
        if (token.now().isBlank) { error.set(Some("Podaj token zaproszenia")); return }
        if (teamName.now().isBlank) { error.set(Some("Podaj nazwę drużyny")); return }
        error.set(None)
        success.set(None)
        busy.set(true)
        App.runZio(ApiClient.acceptInvitation(tok, AcceptInvitationRequest(token.now(), teamName.now()))) {
          case Right(res) =>
            success.set(Some(s"Dołączyłeś do ligi ${res.league.name} jako ${res.team.name}"))
            AppState.invitationToken.set(None)
            AppState.currentPage.set(Page.LeagueView(res.league.id))
            busy.set(false)
          case Left(m) =>
            error.set(Some(m))
            busy.set(false)
        }
    }

    div(
      cls := "max-w-md mx-auto",
      button(
        cls := "mb-4 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-700 rounded hover:bg-gray-300",
        "← Anuluj",
        onClick --> { _ => goBack() }
      ),
      h1(cls := "text-xl font-bold mb-4", "Akceptuj zaproszenie do ligi"),
      div(
        cls := "mb-4",
        label(cls := "block text-sm font-medium mb-1", "Token zaproszenia"),
        input(
          typ := "text",
          cls := "w-full px-3 py-2 border rounded dark:bg-gray-700",
          placeholder := "Wklej token z e-maila",
          controlled(value <-- token.signal, onInput.mapToValue --> token.writer)
        )
      ),
      div(
        cls := "mb-4",
        label(cls := "block text-sm font-medium mb-1", "Nazwa twojej drużyny"),
        input(
          typ := "text",
          cls := "w-full px-3 py-2 border rounded dark:bg-gray-700",
          placeholder := "Nazwa drużyny",
          controlled(value <-- teamName.signal, onInput.mapToValue --> teamName.writer)
        )
      ),
      child <-- error.signal.map {
        case Some(m) => div(cls := "text-red-600 text-sm mb-2", m)
        case None    => emptyNode
      },
      child <-- success.signal.map {
        case Some(m) => div(cls := "text-green-600 text-sm mb-2", m)
        case None    => emptyNode
      },
      button(
        cls := "w-full py-2 px-4 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50",
        disabled <-- busy.signal,
        "Dołącz do ligi",
        onClick --> { _ => submit() }
      )
    )
  }
}
