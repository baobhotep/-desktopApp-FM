package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*
import org.scalajs.dom.document
import org.scalajs.dom.window

object Main {
  private val TokenKey = "fm-game-jwt"

  def main(args: Array[String]): Unit = {
    // API base URL: from meta name="api-base" or default
    Option(document.querySelector("meta[name=api-base]")).flatMap(m => Option(m.getAttribute("content")).filter(_.nonEmpty)).foreach { url =>
      AppState.apiBaseUrl.set(url)
    }
    // Restore session from localStorage
    Option(window.localStorage.getItem(TokenKey)).filter(_.nonEmpty).foreach { savedToken =>
      AppState.token.set(Some(savedToken))
      App.runZio(ApiClient.me(savedToken)) {
        case Right(user) => AppState.currentUser.set(Some(user))
        case _           => window.localStorage.removeItem(TokenKey); AppState.token.set(None)
      }
    }

    // Prefill invitation token from URL (?invitation=TOKEN)
    val search = Option(window.location.search).filter(_.nonEmpty)
    search.foreach { qs =>
      if (qs.startsWith("?")) {
        val params = qs.drop(1).split("&").flatMap { param =>
          val i = param.indexOf('=')
          if (i >= 0) Some(param.take(i) -> param.drop(i + 1)) else None
        }.toMap
        params.get("invitation").filter(_.nonEmpty).foreach(t => AppState.invitationToken.set(Some(t)))
      }
    }

    val container = document.getElementById("app")
    if (container != null) {
      val app = App.rootElement
      render(container, app)
    }
  }
}
