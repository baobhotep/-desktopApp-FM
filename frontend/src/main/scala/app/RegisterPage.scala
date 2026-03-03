package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*

object RegisterPage {
  def render(showLogin: Var[Boolean]): Element = {
    val email = Var("")
    val password = Var("")
    val displayName = Var("")
    val error = Var[Option[String]](None)
    val busy = Var(false)

    def doRegister(): Unit = {
      error.set(None)
      busy.set(true)
      App.runZio(ApiClient.register(RegisterRequest(email.now(), password.now(), displayName.now()))) {
        case Right(_) =>
          error.set(None)
          busy.set(false)
          showLogin.set(true)
        case Left(msg) =>
          error.set(Some(msg))
          busy.set(false)
      }
    }

    div(
      cls := "max-w-sm mx-auto mt-8 p-6 rounded-lg shadow bg-white dark:bg-gray-800",
      h1(cls := "text-xl font-bold mb-4", "Register"),
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
        label(cls := "block text-sm font-medium mb-1", "Display name"),
        input(
          typ := "text",
          cls := "w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600",
          controlled(
            value <-- displayName.signal,
            onInput.mapToValue --> displayName.writer
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
        "Register",
        onClick --> { _ => doRegister() }
      ),
      button(
        cls := "w-full mt-2 py-2 text-blue-600 dark:text-blue-400",
        "Back to login",
        onClick --> { _ => showLogin.set(true) }
      )
    )
  }
}
