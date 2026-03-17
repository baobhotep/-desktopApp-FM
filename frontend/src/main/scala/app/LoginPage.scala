package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*

object LoginPage {
  def render: Element = {
    val email = Var("")
    val password = Var("")
    val error = Var[Option[String]](None)
    val busy = Var(false)

    def doLogin(): Unit = {
      val e = email.now().trim
      val p = password.now()
      if (e.isEmpty || !e.contains("@")) { error.set(Some("Podaj poprawny adres e-mail")); return }
      if (p.isEmpty) { error.set(Some("Podaj hasło")); return }
      error.set(None)
      busy.set(true)
      App.runZio(ApiClient.login(LoginRequest(e, p))) {
        case Right(login) =>
          org.scalajs.dom.window.localStorage.setItem(AuthConstants.TokenKey, login.token)
          AppState.token.set(Some(login.token))
          AppState.currentUser.set(Some(login.user))
          AppState.currentPage.set(Page.Dashboard)
          error.set(None)
          busy.set(false)
        case Left(msg) =>
          error.set(Some(msg))
          busy.set(false)
      }
    }

    div(
      cls := "max-w-sm mx-auto mt-8 p-6 rounded-lg shadow bg-white dark:bg-gray-800",
      h1(cls := "text-xl font-bold mb-4", "Log in"),
      div(
        cls := "mb-4",
        label(cls := "block text-sm font-medium mb-1", "Email"),
        input(
          typ := "email",
          cls := "w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600",
          controlled(
            value <-- email.signal,
            onInput.mapToValue --> email.writer
          )
        )
      ),
      div(
        cls := "mb-4",
        label(cls := "block text-sm font-medium mb-1", "Password"),
        input(
          typ := "password",
          cls := "w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600",
          controlled(
            value <-- password.signal,
            onInput.mapToValue --> password.writer
          )
        )
      ),
      child <-- error.signal.map {
        case Some(msg) => div(cls := "text-red-600 dark:text-red-400 mb-2 text-sm", msg)
        case None      => emptyNode
      },
      button(
        cls := "w-full py-2 px-4 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50",
        disabled <-- busy.signal,
        "Log in",
        onClick --> { _ => doLogin() }
      ),
      button(
        cls := "w-full mt-2 py-2 text-blue-600 dark:text-blue-400",
        "Register instead",
        onClick --> { _ => AppState.currentPage.set(Page.Register) }
      )
    )
  }
}
