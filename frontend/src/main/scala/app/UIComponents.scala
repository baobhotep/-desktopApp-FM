package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*

/** Wspólne komponenty UI: karta zawodnika, tabela statystyk, edytor tygodnia treningowego. */
object UIComponents {

  /** Karta jednego zawodnika (imię, nazwisko, pozycje). */
  def playerCard(p: PlayerDto): Element =
    div(
      cls := "p-2 rounded border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 flex justify-between items-center",
      span(cls := "font-medium", s"${p.firstName} ${p.lastName}"),
      span(cls := "text-sm text-gray-500 dark:text-gray-400", p.preferredPositions.mkString(", "))
    )

  /** Tabela zaawansowanych statystyk (Data Hub). */
  def statsTable(rows: List[PlayerSeasonAdvancedStatsRowDto], maxRows: Int = 50): Element =
    div(
      cls := "overflow-x-auto mb-6",
      table(
        cls := "w-full border-collapse border border-gray-300 dark:border-gray-600 text-sm",
        thead(
          tr(
            th(cls := "border p-2 text-left", "Zawodnik"),
            th(cls := "border p-2 text-left", "Drużyna"),
            th(cls := "border p-2", "M"),
            th(cls := "border p-2", "Min"),
            th(cls := "border p-2", "G"),
            th(cls := "border p-2", "A"),
            th(cls := "border p-2", "S"),
            th(cls := "border p-2", "SoT"),
            th(cls := "border p-2", "xG"),
            th(cls := "border p-2", "KP"),
            th(cls := "border p-2", "P"),
            th(cls := "border p-2", "Cmp"),
            th(cls := "border p-2", "Tkl"),
            th(cls := "border p-2", "Int")
          )
        ),
        tbody(
          rows.take(maxRows).map { r =>
            tr(
              td(cls := "border p-2", r.playerName),
              td(cls := "border p-2", r.teamName),
              td(cls := "border p-2 text-center", r.matches.toString),
              td(cls := "border p-2 text-center", r.minutes.toString),
              td(cls := "border p-2 text-center", r.goals.toString),
              td(cls := "border p-2 text-center", r.assists.toString),
              td(cls := "border p-2 text-center", r.shots.toString),
              td(cls := "border p-2 text-center", r.shotsOnTarget.toString),
              td(cls := "border p-2 text-center", f"${r.xg}%.3f"),
              td(cls := "border p-2 text-center", r.keyPasses.toString),
              td(cls := "border p-2 text-center", r.passes.toString),
              td(cls := "border p-2 text-center", r.passesCompleted.toString),
              td(cls := "border p-2 text-center", r.tackles.toString),
              td(cls := "border p-2 text-center", r.interceptions.toString)
            )
          }.toSeq
        )
      ),
      p(cls := "text-xs text-gray-500 mt-1", s"Top $maxRows (sort: gole, asysty, xG).")
    )

}
