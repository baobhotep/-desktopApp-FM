package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*
import scala.scalajs.js.timers.setTimeout

object LeaguePage {
  def render(leagueId: String, goBack: () => Unit): Element = {
    val league = Var[Option[LeagueDto]](None)
    val tableRows = Var[List[TableRowDto]](Nil)
    val fixtures = Var[List[MatchDto]](Nil)
    val fixturesLoadingMore = Var(false)
    val FixturesPageSize = 20
    val teams = Var[List[TeamDto]](Nil)
    val error = Var[Option[String]](None)
    val playBusy = Var(false)
    val playError = Var[Option[String]](None)
    val transferWindows = Var[List[TransferWindowDto]](Nil)
    val transferOffers = Var[List[TransferOfferDto]](Nil)
    val newOfferWindowId = Var("")
    val newOfferToTeamId = Var("")
    val newOfferPlayerId = Var("")
    val newOfferAmount = Var("")
    val newOfferError = Var[Option[String]](None)
    val newOfferBusy = Var(false)
    val offerActionBusy = Var(false)
    val selectedTeamPlayers = Var[List[PlayerDto]](Nil)
    val setupInviteEmail = Var("")
    val setupInviteError = Var[Option[String]](None)
    val setupInviteBusy = Var(false)
    val setupAddBotsCount = Var("1")
    val setupAddBotsError = Var[Option[String]](None)
    val setupAddBotsBusy = Var(false)
    val setupStartError = Var[Option[String]](None)
    val setupStartBusy = Var(false)
    val playerStats = Var[Option[LeaguePlayerStatsDto]](None)
    val advStats = Var[Option[LeaguePlayerAdvancedStatsDto]](None)
    val scoutingList = Var[List[LeaguePlayerRowDto]](Nil)
    val scoutingPos = Var("")
    val scoutingMinOverall = Var("12")
    val scoutingQ = Var("")
    val scoutingBusy = Var(false)
    val scoutingErr = Var[Option[String]](None)
    val shortlist = Var[List[ShortlistEntryDto]](Nil)
    val reports = Var[List[ScoutingReportDto]](Nil)
    val reportTarget = Var("")
    val reportRating = Var("7.0")
    val reportNotes = Var("")
    val reportBusy = Var(false)

    def doInvite(): Unit = AppState.token.now() match {
      case None => setupInviteError.set(Some("Zaloguj się"))
      case Some(tok) =>
        val email = setupInviteEmail.now().trim
        if (email.isEmpty) { setupInviteError.set(Some("Podaj e-mail")); return }
        setupInviteError.set(None)
        setupInviteBusy.set(true)
        App.runZio(ApiClient.inviteToLeague(tok, leagueId, email)) {
          case Right(_) => load(); setupInviteEmail.set(""); setupInviteBusy.set(false)
          case Left(m)  => setupInviteError.set(Some(m)); setupInviteBusy.set(false)
        }
    }
    def doAddBots(): Unit = AppState.token.now() match {
      case None => setupAddBotsError.set(Some("Zaloguj się"))
      case Some(tok) =>
        val n = setupAddBotsCount.now().toIntOption.getOrElse(0)
        if (n < 1) { setupAddBotsError.set(Some("Podaj liczbę botów ≥ 1")); return }
        setupAddBotsError.set(None)
        setupAddBotsBusy.set(true)
        App.runZio(ApiClient.addBots(tok, leagueId, n)) {
          case Right(_) => load(); setupAddBotsBusy.set(false)
          case Left(m)  => setupAddBotsError.set(Some(m)); setupAddBotsBusy.set(false)
        }
    }
    def doStartSeason(): Unit = AppState.token.now() match {
      case None => setupStartError.set(Some("Zaloguj się"))
      case Some(tok) =>
        setupStartError.set(None)
        setupStartBusy.set(true)
        App.runZio(ApiClient.startSeason(tok, leagueId, None)) {
          case Right(_) => load(); setupStartBusy.set(false)
          case Left(m)  => setupStartError.set(Some(m)); setupStartBusy.set(false)
        }
    }

    def doAcceptOffer(offerId: String): Unit = AppState.token.now() match {
      case None => ()
      case Some(tok) =>
        offerActionBusy.set(true)
        App.runZio(ApiClient.acceptTransferOffer(tok, offerId)) {
          case Right(_) => load(); offerActionBusy.set(false)
          case Left(m)  => newOfferError.set(Some(m)); offerActionBusy.set(false)
        }
    }
    def doRejectOffer(offerId: String): Unit = AppState.token.now() match {
      case None => ()
      case Some(tok) =>
        offerActionBusy.set(true)
        App.runZio(ApiClient.rejectTransferOffer(tok, offerId)) {
          case Right(_) => load(); offerActionBusy.set(false)
          case Left(m)  => newOfferError.set(Some(m)); offerActionBusy.set(false)
        }
    }
    def doCreateOffer(): Unit = AppState.token.now() match {
      case None => newOfferError.set(Some("Zaloguj się"))
      case Some(tok) =>
        val winId = newOfferWindowId.now()
        val toId = newOfferToTeamId.now()
        val playerId = newOfferPlayerId.now()
        val amountStr = newOfferAmount.now()
        if (winId.isEmpty || toId.isEmpty || playerId.isEmpty) { newOfferError.set(Some("Uzupełnij okno, drużynę i zawodnika")); return }
        val amount = amountStr.toDoubleOption.getOrElse(0.0)
        if (amount <= 0) { newOfferError.set(Some("Kwota musi być > 0")); return }
        newOfferError.set(None)
        newOfferBusy.set(true)
        App.runZio(ApiClient.createTransferOffer(tok, leagueId, CreateTransferOfferRequest(winId, toId, playerId, amount))) {
          case Right(_) => load(); newOfferAmount.set(""); newOfferPlayerId.set(""); newOfferBusy.set(false)
          case Left(m)  => newOfferError.set(Some(m)); newOfferBusy.set(false)
        }
    }

    /** Ładuje dane priorytetowe: liga, tabela, terminarz, drużyny – od razu, żeby tabela i terminarz się narysowały. */
    def loadPrimary(): Unit = AppState.token.now() match {
      case None => error.set(Some("Not logged in"))
      case Some(tok) =>
        App.runZio(ApiClient.getLeague(tok, leagueId)) {
          case Right(l) => league.set(Some(l))
          case Left(m)  => error.set(Some(m))
        }
        App.runZio(ApiClient.getTable(tok, leagueId)) {
          case Right(t) => tableRows.set(t)
          case Left(_)  =>
        }
        App.runZio(ApiClient.getFixtures(leagueId, Some(FixturesPageSize), Some(0))) {
          case Right(f) => fixtures.set(f)
          case Left(_)  =>
        }
        App.runZio(ApiClient.getTeams(leagueId)) {
          case Right(ts) => teams.set(ts)
          case Left(_)   =>
        }
    }

    /** Ładuje statystyki, okna transferowe i oferty – w tle, po pierwszym renderze. */
    def loadSecondary(): Unit = AppState.token.now() match {
      case None => ()
      case Some(tok) =>
        App.runZio(ApiClient.getLeaguePlayerStats(tok, leagueId)) {
          case Right(s) => playerStats.set(Some(s))
          case Left(_)  => playerStats.set(None)
        }
        App.runZio(ApiClient.getLeaguePlayerAdvancedStats(tok, leagueId)) {
          case Right(s) => advStats.set(Some(s))
          case Left(_)  => advStats.set(None)
        }
        App.runZio(ApiClient.getTransferWindows(leagueId)) {
          case Right(w) => transferWindows.set(w)
          case Left(_)  =>
        }
        App.runZio(ApiClient.getTransferOffers(leagueId, None)) {
          case Right(o) => transferOffers.set(o)
          case Left(_)  =>
        }
    }

    def load(): Unit = {
      loadPrimary()
      setTimeout(50) { loadSecondary() }
    }
    def loadMoreFixtures(): Unit = AppState.token.now() match {
      case None => ()
      case Some(_) =>
        val offset = fixtures.now().size
        fixturesLoadingMore.set(true)
        App.runZio(ApiClient.getFixtures(leagueId, Some(FixturesPageSize), Some(offset))) {
          case Right(more) =>
            fixtures.update(_ ++ more)
            fixturesLoadingMore.set(false)
          case Left(_) => fixturesLoadingMore.set(false)
        }
    }

    def doPlayMatchday(): Unit = AppState.token.now() match {
      case None => playError.set(Some("Not logged in"))
      case Some(tok) =>
        playBusy.set(true)
        playError.set(None)
        App.runZio(ApiClient.playMatchday(tok, leagueId)) {
          case Right(_) => load(); playBusy.set(false)
          case Left(m)  => playError.set(Some(m)); playBusy.set(false)
        }
    }

    load()

    def myTeamIdOpt(ts: List[TeamDto], userOpt: Option[UserDto]): Option[String] =
      userOpt.flatMap(u => ts.find(_.ownerUserId.contains(u.id)).map(_.id))

    def refreshScouting(): Unit = AppState.token.now() match {
      case None => ()
      case Some(tok) =>
        scoutingBusy.set(true)
        scoutingErr.set(None)
        App.runZio(ApiClient.listLeaguePlayers(tok, leagueId, Option(scoutingPos.now()).filter(_.nonEmpty), Option(scoutingMinOverall.now()).filter(_.nonEmpty), Option(scoutingQ.now()).filter(_.nonEmpty))) {
          case Right(dto) => scoutingList.set(dto.players); scoutingBusy.set(false)
          case Left(m)    => scoutingErr.set(Some(m)); scoutingBusy.set(false)
        }
    }

    def refreshShortlist(teamId: String): Unit = AppState.token.now().foreach { tok =>
      App.runZio(ApiClient.getShortlist(tok, teamId)) {
        case Right(l) => shortlist.set(l)
        case Left(_)  => shortlist.set(Nil)
      }
      App.runZio(ApiClient.getScoutingReports(tok, teamId)) {
        case Right(l) => reports.set(l)
        case Left(_)  => reports.set(Nil)
      }
    }

    val exportMatchIds = Var("")
    val exportFormat = Var("csv")
    val exportBusy = Var(false)
    val exportError = Var[Option[String]](None)
    val exportResult = Var[Option[String]](None)
    /** Filtry eksportu: kolejka od–do, drużyna (gdy ustawione + leagueId = bieżąca liga, backend dobierze mecze). */
    val exportFromMatchday = Var("")
    val exportToMatchday = Var("")
    val exportTeamId = Var("")
    /** Typy zdarzeń w eksporcie (puste = wszystkie); np. Pass,Shot,Goal. */
    val exportEventTypes = Var("")
    /** Zaznaczone mecze z terminarza do eksportu (checkboxy). */
    val exportSelectedMatchIds = Var[Set[String]](Set.empty)
    /** H2H: drużyna 1, drużyna 2, wynik. */
    val h2hTeam1 = Var("")
    val h2hTeam2 = Var("")
    val h2hResult = Var[Option[Either[String, List[MatchDto]]]](None)
    val h2hBusy = Var(false)
    /** Prognoza kolejki. */
    val prognosisMatchday = Var("")
    val prognosisResult = Var[Option[Either[String, List[MatchPrognosisDto]]]](None)
    val prognosisBusy = Var(false)
    /** Porównanie zawodników. */
    val comparePlayer1 = Var("")
    val comparePlayer2 = Var("")
    val compareResult = Var[Option[Either[String, ComparePlayersDto]]](None)
    val compareBusy = Var(false)
    /** Ile meczów terminarza pokazać (wirtualizacja – potem „Pokaż kolejne 15”). */
    val visibleFixturesCount = Var(15)
    /** Sortowanie tabeli: klucz kolumny i kierunek. */
    val tableSortKey = Var("position")
    val tableSortDesc = Var(false)
    /** Sortowanie Data Hub. */
    val dataHubSortKey = Var("goals")
    val dataHubSortDesc = Var(true)

    def sortTableRows(rows: List[TableRowDto], key: String, desc: Boolean): List[TableRowDto] = {
      val sorted = key match {
        case "team"  => rows.sortBy(_.teamName)
        case "pts"   => rows.sortBy(_.points)
        case "p"     => rows.sortBy(_.played)
        case "wdl"   => rows.sortBy(r => (r.won, r.drawn, r.lost))
        case "gfga"  => rows.sortBy(r => (r.goalsFor, r.goalsAgainst))
        case "gd"    => rows.sortBy(_.goalDifference)
        case _       => rows.sortBy(_.position)
      }
      if (desc) sorted.reverse else sorted
    }

    def toggleTableSort(key: String): Unit = {
      if (tableSortKey.now() == key) tableSortDesc.update(!_)
      else { tableSortKey.set(key); tableSortDesc.set(false) }
    }

    def sortAdvStatsRows(rows: List[PlayerSeasonAdvancedStatsRowDto], key: String, desc: Boolean): List[PlayerSeasonAdvancedStatsRowDto] = {
      val sorted = key match {
        case "assists"   => rows.sortBy(_.assists)
        case "xg"        => rows.sortBy(_.xg)
        case "minutes"   => rows.sortBy(_.minutes)
        case "playerName"=> rows.sortBy(_.playerName)
        case "teamName"  => rows.sortBy(_.teamName)
        case _           => rows.sortBy(_.goals)
      }
      if (desc) sorted.reverse else sorted
    }

    def downloadExportAsFile(content: String, format: String): Unit = {
      val ext = if (format == "csv") "csv" else "json"
      val mime = if (format == "csv") "text/csv;charset=utf-8" else "application/json;charset=utf-8"
      val dataUrl = s"data:$mime,${scala.scalajs.js.URIUtils.encodeURIComponent(content)}"
      val a = org.scalajs.dom.document.createElement("a").asInstanceOf[org.scalajs.dom.html.Anchor]
      a.setAttribute("href", dataUrl)
      a.setAttribute("download", s"match-logs-export.$ext")
      org.scalajs.dom.document.body.appendChild(a)
      a.click()
      org.scalajs.dom.document.body.removeChild(a)
    }

    def addSelectedToExport(): Unit = {
      val cur = exportMatchIds.now().split("[,\\s]+").toList.map(_.trim).filter(_.nonEmpty).toSet
      val toAdd = exportSelectedMatchIds.now().take(50 - cur.size)
      exportMatchIds.set((cur ++ toAdd).mkString(" "))
    }

    def doExport(): Unit = AppState.token.now() match {
      case None => exportError.set(Some("Zaloguj się"))
      case Some(tok) =>
        val useFilter = exportFromMatchday.now().trim.nonEmpty || exportToMatchday.now().trim.nonEmpty || exportTeamId.now().trim.nonEmpty
        val ids = if (useFilter) Nil else exportMatchIds.now().split("[,\\s]+").toList.map(_.trim).filter(_.nonEmpty)
        if (!useFilter && ids.isEmpty) { exportError.set(Some("Podaj ID meczów lub wypełnij filtry (kolejka/drużyna)")); return }
        if (ids.size > 50) { exportError.set(Some("Maks. 50 meczów")); return }
        val fmt = exportFormat.now().trim.toLowerCase
        if (!Set("csv", "json", "json-full").contains(fmt)) { exportError.set(Some("Format: csv, json lub json-full")); return }
        val leagueIdOpt = if (useFilter) Some(leagueId) else None
        val fromMd = exportFromMatchday.now().trim.toIntOption
        val toMd = exportToMatchday.now().trim.toIntOption
        val teamOpt = exportTeamId.now().trim match { case "" => None; case t => Some(t) }
        val eventTypesList = exportEventTypes.now().split("[,\\s]+").toList.map(_.trim).filter(_.nonEmpty) match { case Nil => None; case l => Some(l) }
        exportBusy.set(true)
        exportError.set(None)
        exportResult.set(None)
        App.runZio(ApiClient.exportMatchLogs(tok, ids, fmt, leagueIdOpt, fromMd, toMd, teamOpt, eventTypesList)) {
          case Right(text) =>
            exportBusy.set(false)
            exportResult.set(Some(text))
          case Left(m) =>
            exportBusy.set(false)
            exportError.set(Some(m))
        }
    }

    div(
      cls := "max-w-4xl mx-auto",
      button(
        cls := "mb-4 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-700 rounded hover:bg-gray-300",
        "← Back",
        onClick --> { _ => goBack() }
      ),
      child <-- error.signal.map {
        case Some(m) => div(cls := "text-red-600 dark:text-red-400 mb-4", m)
        case None    => emptyNode
      },
      child <-- league.signal.map {
        case Some(l) =>
          if (l.seasonPhase == "Setup") {
            div(
              h1(cls := "text-xl font-bold mb-2", l.name),
              p(cls := "text-sm text-gray-500 dark:text-gray-400 mb-4", "Faza konfiguracji · uzupełnij sloty drużyn"),
              h2(cls := "text-lg font-semibold mb-2", "Drużyny"),
              div(
                cls := "space-y-2 mb-6",
                children <-- teams.signal.map { ts =>
                  (1 to l.teamCount).map { i =>
                    ts.lift(i - 1) match {
                      case Some(t) =>
                        div(cls := "p-2 rounded bg-gray-100 dark:bg-gray-700 flex justify-between items-center",
                          span(t.name + t.managerName.fold("")(" (" + _ + ")")),
                          span(cls := "text-sm", if (t.ownerType == "Human") "Gracz" else "Bot", " · Elo ", span(cls := "font-mono", t.eloRating.toInt.toString))
                        )
                      case None =>
                        div(cls := "p-2 rounded bg-gray-50 dark:bg-gray-800 text-gray-500", s"Slot $i — wolny")
                    }
                  }.toSeq
                }
              ),
              h3(cls := "font-medium mb-2", "Zaproszenie (e-mail)"),
              div(cls := "flex gap-2 mb-2",
                input(typ := "email", cls := "flex-1 px-2 py-1 border rounded dark:bg-gray-700", placeholder := "email@example.com",
                  controlled(value <-- setupInviteEmail.signal, onInput.mapToValue --> setupInviteEmail.writer)),
                button(cls := "px-4 py-1 bg-blue-600 text-white rounded", disabled <-- setupInviteBusy.signal, "Zaproś", onClick --> { _ => doInvite() })
              ),
              child <-- setupInviteError.signal.map { case Some(m) => div(cls := "text-red-600 text-sm mb-2", m); case None => emptyNode },
              h3(cls := "font-medium mt-4 mb-2", "Dodaj boty"),
              div(cls := "flex gap-2 mb-2",
                input(typ := "number", cls := "w-20 px-2 py-1 border rounded dark:bg-gray-700", placeholder := "1",
                  controlled(value <-- setupAddBotsCount.signal, onInput.mapToValue --> setupAddBotsCount.writer)),
                button(cls := "px-4 py-1 bg-gray-600 text-white rounded", disabled <-- setupAddBotsBusy.signal, "Dodaj boty", onClick --> { _ => doAddBots() })
              ),
              child <-- setupAddBotsError.signal.map { case Some(m) => div(cls := "text-red-600 text-sm mb-2", m); case None => emptyNode },
              h3(cls := "font-medium mt-4 mb-2", "Start sezonu"),
              child <-- setupStartError.signal.map { case Some(m) => div(cls := "text-red-600 text-sm mb-2", m); case None => emptyNode },
              div(cls := "flex gap-2",
                button(
                  cls := "px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50",
                  disabled <-- (teams.signal.combineWith(setupStartBusy.signal)).map { case (ts, busy) => busy || ts.size < l.teamCount },
                  "Rozpocznij sezon",
                  onClick --> { _ => doStartSeason() }
                )
              )
            )
          } else {
            div(
              h1(cls := "text-xl font-bold mb-2", l.name),
              p(cls := "text-sm text-gray-500 dark:text-gray-400 mb-4", s"Matchday ${l.currentMatchday}/${l.totalMatchdays} · ${l.seasonPhase}"),
              child <-- playError.signal.map {
                case Some(m) => div(cls := "text-red-600 text-sm mb-2", m)
                case None    => emptyNode
              },
              button(
                cls := "mb-6 px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50",
                disabled <-- playBusy.signal,
                "Play matchday",
                onClick --> { _ => doPlayMatchday() }
              ),
              h2(cls := "text-lg font-semibold mb-2", "Tabela"),
            table(
              cls := "w-full border-collapse border border-gray-300 dark:border-gray-600",
              thead(
                tr(
                  th(cls := "border p-2 text-left cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700", "↕ #", onClick --> { _ => toggleTableSort("position") }),
                  th(cls := "border p-2 text-left cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700", "↕ Drużyna", onClick --> { _ => toggleTableSort("team") }),
                  th(cls := "border p-2 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700", "↕ Pkt", onClick --> { _ => toggleTableSort("pts") }),
                  th(cls := "border p-2 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700", "↕ M", onClick --> { _ => toggleTableSort("p") }),
                  th(cls := "border p-2 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700", "↕ W-R-P", onClick --> { _ => toggleTableSort("wdl") }),
                  th(cls := "border p-2 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700", "↕ GZ-GS", onClick --> { _ => toggleTableSort("gfga") }),
                  th(cls := "border p-2 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700", "↕ Bilans", onClick --> { _ => toggleTableSort("gd") })
                )
              ),
              tbody(
                children <-- tableRows.signal.combineWith(tableSortKey.signal).combineWith(tableSortDesc.signal).map { case (rows, key, desc) =>
                  sortTableRows(rows, key, desc).map { r =>
                    tr(
                      td(cls := "border p-2", r.position.toString),
                      td(
                        cls := "border p-2",
                        button(
                          cls := "text-left text-blue-600 dark:text-blue-400 hover:underline",
                          r.teamName,
                          onClick --> { _ => AppState.selectedTeamId.set(Some(r.teamId)) }
                        )
                      ),
                      td(cls := "border p-2", r.points.toString),
                      td(cls := "border p-2", r.played.toString),
                      td(cls := "border p-2", s"${r.won}-${r.drawn}-${r.lost}"),
                      td(cls := "border p-2", s"${r.goalsFor}-${r.goalsAgainst}"),
                      td(cls := "border p-2", r.goalDifference.toString)
                    )
                  }
                }
              )
            ),
            h2(cls := "text-lg font-semibold mt-6 mb-2", "Statystyki zawodników"),
            child <-- playerStats.signal.map {
              case None => div(cls := "text-gray-500 text-sm mb-4", "Brak danych statystyk lub sezon jeszcze się nie rozpoczął.")
              case Some(ps) =>
                div(
                  cls := "grid grid-cols-1 md:grid-cols-2 gap-4 mb-4 text-sm",
                  div(
                    h3(cls := "font-semibold mb-1", "Król strzelców"),
                    if (ps.topScorers.isEmpty) div(cls := "text-gray-500", "Brak goli")
                    else
                      ul(
                        cls := "space-y-1",
                        ps.topScorers.map { r =>
                          li(s"${r.playerName} (${r.teamName}) – ${r.goals} goli, ${r.assists} asyst")
                        }
                      )
                  ),
                  div(
                    h3(cls := "font-semibold mb-1", "Lider asyst"),
                    if (ps.topAssists.isEmpty) div(cls := "text-gray-500", "Brak asyst")
                    else
                      ul(
                        cls := "space-y-1",
                        ps.topAssists.map { r =>
                          li(s"${r.playerName} (${r.teamName}) – ${r.assists} asyst, ${r.goals} goli")
                        }
                      )
                  )
                )
            },
            h3(cls := "text-base font-semibold mt-2 mb-2", "Data Hub (zaawansowane)"),
            child <-- advStats.signal.combineWith(dataHubSortKey.signal).combineWith(dataHubSortDesc.signal).map {
              case (None, _, _) => div(cls := "text-gray-500 text-sm mb-4", "Brak danych.")
              case (Some(s), sortKey, sortDesc) =>
                if (s.rows.isEmpty) div(cls := "text-gray-500 text-sm mb-4", "Brak statystyk.")
                else {
                  val sorted = sortAdvStatsRows(s.rows, sortKey, sortDesc)
                  div(
                    div(
                      cls := "flex items-center gap-2 mb-2",
                      label(cls := "text-xs", "Sortuj po:"),
                      select(
                        cls := "px-2 py-1 border rounded dark:bg-gray-700 text-xs",
                        option(value := "goals", "Gole"),
                        option(value := "assists", "Asysty"),
                        option(value := "xg", "xG"),
                        option(value := "minutes", "Minuty"),
                        option(value := "playerName", "Zawodnik"),
                        option(value := "teamName", "Drużyna"),
                        value <-- dataHubSortKey.signal,
                        onChange --> { e => dataHubSortKey.set(e.target.asInstanceOf[org.scalajs.dom.html.Select].value) }
                      ),
                      label(cls := "inline-flex items-center gap-1 text-xs",
                        input(typ := "checkbox", checked <-- dataHubSortDesc.signal, onChange --> { _ => dataHubSortDesc.update(!_) }),
                        "Malejąco"
                      )
                    ),
                    UIComponents.statsTable(sorted, 50)
                  )
                }
            },
            div(
              cls := "mt-6 p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-sm space-y-2",
              h3(cls := "text-base font-semibold mb-1", "Eksport logów meczów"),
              p(cls := "text-xs text-gray-500", "ID meczów lub filtry: kolejka od–do, drużyna (wtedy wyeksportowane zostaną mecze z bieżącej ligi). Opcjonalnie typy zdarzeń (np. Pass,Shot,Goal)."),
              div(
                cls := "grid grid-cols-1 md:grid-cols-2 gap-2 mb-2",
                div(
                  label(cls := "block text-xs mb-1", "Kolejka od"),
                  input(typ := "number", cls := "w-full px-2 py-1 border rounded dark:bg-gray-700 text-xs", placeholder := "np. 1",
                    value <-- exportFromMatchday.signal, onInput.mapToValue --> exportFromMatchday.writer)
                ),
                div(
                  label(cls := "block text-xs mb-1", "Kolejka do"),
                  input(typ := "number", cls := "w-full px-2 py-1 border rounded dark:bg-gray-700 text-xs", placeholder := "np. 10",
                    value <-- exportToMatchday.signal, onInput.mapToValue --> exportToMatchday.writer)
                ),
                div(cls := "md:col-span-2",
                  label(cls := "block text-xs mb-1", "Drużyna (opcjonalnie)"),
                  select(
                    cls := "w-full px-2 py-1 border rounded dark:bg-gray-700 text-xs",
                    value <-- exportTeamId.signal,
                    onChange.mapToValue --> exportTeamId.writer,
                    option(value := "", "— wszystkie —"),
                    children <-- teams.signal.map(ts => ts.map(t => option(value := t.id, t.name)).toSeq)
                  )
                ),
                div(cls := "md:col-span-2",
                  label(cls := "block text-xs mb-1", "Typy zdarzeń (opcjonalnie, po przecinku: Pass, Shot, Goal)"),
                  input(typ := "text", cls := "w-full px-2 py-1 border rounded dark:bg-gray-700 text-xs", placeholder := "np. Pass, Shot, Goal",
                    value <-- exportEventTypes.signal, onInput.mapToValue --> exportEventTypes.writer)
                )
              ),
              div(
                cls := "flex flex-col md:flex-row gap-2",
                textArea(
                  cls := "flex-1 px-2 py-1 border rounded dark:bg-gray-700 text-xs font-mono min-h-[60px]",
                  placeholder := "ID meczów (gdy nie używasz filtrów): match-1 match-2",
                  value <-- exportMatchIds.signal,
                  onInput.mapToValue --> exportMatchIds.writer
                ),
                div(
                  cls := "w-full md:w-40 space-y-2",
                  div(
                    label(cls := "block text-xs mb-1", "Format"),
                    select(
                      cls := "w-full px-2 py-1 border rounded dark:bg-gray-700 text-xs",
                      option(value := "csv", "csv"),
                      option(value := "json", "json"),
                      option(value := "json-full", "json (z pełną analityką)"),
                      value <-- exportFormat.signal,
                      onChange.mapToValue --> exportFormat.writer
                    )
                  ),
                  button(
                    cls := "w-full px-3 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 disabled:opacity-50",
                    disabled <-- exportBusy.signal,
                    "Eksportuj",
                    onClick --> { _ => doExport() }
                  )
                )
              ),
              child <-- exportError.signal.map {
                case Some(m) => div(cls := "text-xs text-red-500", m)
                case None    => emptyNode
              },
              child <-- exportResult.signal.map {
                case Some(text) =>
                  div(
                    h4(cls := "text-xs font-semibold mb-1", "Wynik"),
                    button(
                      cls := "mb-2 px-3 py-1 text-xs bg-green-600 text-white rounded hover:bg-green-700",
                      "Pobierz plik",
                      onClick --> { _ => downloadExportAsFile(text, exportFormat.now()) }
                    ),
                    pre(cls := "whitespace-pre-wrap break-all text-[11px] bg-black/40 text-green-100 p-2 rounded max-h-64 overflow-y-auto", text)
                  )
                case None => emptyNode
              }
            ),
            div(
              cls := "mt-6 p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-sm",
              h3(cls := "text-base font-semibold mb-2", "H2H – ostatnie mecze dwóch drużyn"),
              div(cls := "flex flex-wrap gap-2 items-end mb-2",
                div(
                  label(cls := "block text-xs mb-1", "Drużyna 1"),
                  select(cls := "px-2 py-1 border rounded dark:bg-gray-700 text-xs min-w-[120px]",
                    value <-- h2hTeam1.signal, onChange.mapToValue --> h2hTeam1.writer,
                    option(value := "", "— wybierz —"),
                    children <-- teams.signal.map(ts => ts.map(t => option(value := t.id, t.name)).toSeq)
                  )
                ),
                div(
                  label(cls := "block text-xs mb-1", "Drużyna 2"),
                  select(cls := "px-2 py-1 border rounded dark:bg-gray-700 text-xs min-w-[120px]",
                    value <-- h2hTeam2.signal, onChange.mapToValue --> h2hTeam2.writer,
                    option(value := "", "— wybierz —"),
                    children <-- teams.signal.map(ts => ts.map(t => option(value := t.id, t.name)).toSeq)
                  )
                ),
                button(
                  cls := "px-3 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 disabled:opacity-50",
                  disabled <-- h2hBusy.signal,
                  "Pokaż H2H",
                  onClick --> { _ =>
                    val t1 = h2hTeam1.now(); val t2 = h2hTeam2.now()
                    if (t1.isEmpty || t2.isEmpty) h2hResult.set(Some(Left("Wybierz obie drużyny")))
                    else if (t1 == t2) h2hResult.set(Some(Left("Wybierz różne drużyny")))
                    else AppState.token.now() match {
                      case None => h2hResult.set(Some(Left("Zaloguj się")))
                      case Some(tok) =>
                        h2hBusy.set(true); h2hResult.set(None)
                        App.runZio(ApiClient.getH2H(tok, leagueId, t1, t2, 10)) {
                          case Right(matches) => h2hBusy.set(false); h2hResult.set(Some(Right(matches)))
                          case Left(e) => h2hBusy.set(false); h2hResult.set(Some(Left(e)))
                        }
                    }
                  }
                )
              ),
              child <-- h2hResult.signal.map {
                case None => emptyNode
                case Some(Left(msg)) => div(cls := "text-red-600 text-xs", msg)
                case Some(Right(list)) =>
                  if (list.isEmpty) div(cls := "text-gray-500 text-xs", "Brak meczów H2H.")
                  else ul(cls := "space-y-1 text-xs",
                    list.map(m => {
                      val score = (m.homeGoals, m.awayGoals) match { case (Some(h), Some(a)) => s" $h–$a "; case _ => " – " }
                      li(s"MD${m.matchday} ${m.homeTeamId} $score ${m.awayTeamId}")
                    }).toSeq
                  )
              }
            ),
            div(
              cls := "mt-4 p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-sm",
              h3(cls := "text-base font-semibold mb-2", "Prognoza kolejki (Elo)"),
              div(cls := "flex flex-wrap gap-2 items-end mb-2",
                input(typ := "number", cls := "w-20 px-2 py-1 border rounded dark:bg-gray-700 text-xs", placeholder := "kolejka",
                  value <-- prognosisMatchday.signal, onInput.mapToValue --> prognosisMatchday.writer),
                button(
                  cls := "px-3 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 disabled:opacity-50",
                  disabled <-- prognosisBusy.signal,
                  "Prognoza",
                  onClick --> { _ =>
                    AppState.token.now() match {
                      case None => prognosisResult.set(Some(Left("Zaloguj się")))
                      case Some(tok) =>
                        prognosisBusy.set(true); prognosisResult.set(None)
                        val mdOpt = prognosisMatchday.now().trim.toIntOption
                        App.runZio(ApiClient.getMatchdayPrognosis(tok, leagueId, mdOpt)) {
                          case Right(prog) => prognosisBusy.set(false); prognosisResult.set(Some(Right(prog)))
                          case Left(e) => prognosisBusy.set(false); prognosisResult.set(Some(Left(e)))
                        }
                    }
                  }
                )
              ),
              child <-- prognosisResult.signal.map {
                case None => emptyNode
                case Some(Left(msg)) => div(cls := "text-red-600 text-xs", msg)
                case Some(Right(prog)) =>
                  if (prog.isEmpty) div(cls := "text-gray-500 text-xs", "Brak meczów w tej kolejce.")
                  else ul(cls := "space-y-1 text-xs",
                    prog.map(p => li(
                      span(s"${p.homeName} – ${p.awayName}: "),
                      span(cls := "font-mono", f"P(1)=${p.pHome * 100}%.0f%% P(X)=${p.pDraw * 100}%.0f%% P(2)=${p.pAway * 100}%.0f%%")
                    )).toSeq
                  )
              }
            ),
            div(
              cls := "mt-4 p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-sm",
              h3(cls := "text-base font-semibold mb-2", "Porównanie dwóch zawodników"),
              div(cls := "flex flex-wrap gap-2 items-end mb-2",
                div(
                  label(cls := "block text-xs mb-1", "Zawodnik 1"),
                  select(cls := "px-2 py-1 border rounded dark:bg-gray-700 text-xs min-w-[140px]",
                    value <-- comparePlayer1.signal, onChange.mapToValue --> comparePlayer1.writer,
                    option(value := "", "— wybierz —"),
                    children <-- scoutingList.signal.map(rows => rows.map(r => option(value := r.playerId, s"${r.playerName} (${r.teamName})")).toSeq)
                  )
                ),
                div(
                  label(cls := "block text-xs mb-1", "Zawodnik 2"),
                  select(cls := "px-2 py-1 border rounded dark:bg-gray-700 text-xs min-w-[140px]",
                    value <-- comparePlayer2.signal, onChange.mapToValue --> comparePlayer2.writer,
                    option(value := "", "— wybierz —"),
                    children <-- scoutingList.signal.map(rows => rows.map(r => option(value := r.playerId, s"${r.playerName} (${r.teamName})")).toSeq)
                  )
                ),
                button(
                  cls := "px-3 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 disabled:opacity-50",
                  disabled <-- compareBusy.signal,
                  "Porównaj",
                  onClick --> { _ =>
                    val p1 = comparePlayer1.now(); val p2 = comparePlayer2.now()
                    if (p1.isEmpty || p2.isEmpty) compareResult.set(Some(Left("Wybierz obu zawodników")))
                    else if (p1 == p2) compareResult.set(Some(Left("Wybierz różnych zawodników")))
                    else AppState.token.now() match {
                      case None => compareResult.set(Some(Left("Zaloguj się")))
                      case Some(tok) =>
                        compareBusy.set(true); compareResult.set(None)
                        App.runZio(ApiClient.getComparePlayers(tok, leagueId, p1, p2)) {
                          case Right(data) => compareBusy.set(false); compareResult.set(Some(Right(data)))
                          case Left(e) => compareBusy.set(false); compareResult.set(Some(Left(e)))
                        }
                    }
                  }
                )
              ),
              child <-- compareResult.signal.map {
                case None => emptyNode
                case Some(Left(msg)) => div(cls := "text-red-600 text-xs", msg)
                case Some(Right(data)) =>
                  def playerBlock(player: PlayerDto, st: Option[PlayerSeasonAdvancedStatsRowDto]) = div(cls := "flex-1 p-2 rounded bg-gray-100 dark:bg-gray-700",
                    p(cls := "font-medium", s"${player.firstName} ${player.lastName}"),
                    p(cls := "text-xs", st.map(s => s"Gole: ${s.goals} · Asysty: ${s.assists} · xG: ${f"${s.xg}%.2f"} · Min: ${s.minutes}").getOrElse("Brak statystyk sezonu"))
                  )
                  div(cls := "grid grid-cols-2 gap-2 mt-2",
                    playerBlock(data.player1, data.stats1),
                    playerBlock(data.player2, data.stats2)
                  )
              }
            ),
            h2(cls := "text-lg font-semibold mt-6 mb-2", "Terminarz"),
            p(cls := "text-xs text-gray-500 dark:text-gray-400 mb-1", "Zaznacz mecze i użyj „Dodaj wybrane do eksportu”, aby wypełnić pole ID meczów powyżej."),
            button(
              cls := "mb-2 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-600 rounded hover:bg-gray-300",
              "Dodaj wybrane do eksportu",
              onClick --> { _ => addSelectedToExport() }
            ),
            div(
              cls := "space-y-2",
              children <-- fixtures.signal.combineWith(teams.signal).combineWith(AppState.currentUser.signal).combineWith(fixturesLoadingMore.signal).combineWith(exportSelectedMatchIds.signal).combineWith(visibleFixturesCount.signal).map { case (list, ts, userOpt, loadingMore, _, visibleCount) =>
                val myTeamIdOpt = userOpt.flatMap(u => ts.find(_.ownerUserId.contains(u.id)).map(_.id))
                val showMoreFromApi = list.size >= FixturesPageSize && !loadingMore
                val visibleList = list.take(visibleCount)
                val showMoreFromList = list.size > visibleCount
                val nodes = visibleList.map { m =>
                  val home = ts.find(_.id == m.homeTeamId).map(_.name).getOrElse(m.homeTeamId)
                  val away = ts.find(_.id == m.awayTeamId).map(_.name).getOrElse(m.awayTeamId)
                  val score = (m.homeGoals, m.awayGoals) match {
                    case (Some(h), Some(a)) => s" $h - $a "
                    case _                  => " - "
                  }
                  val isMyMatch = myTeamIdOpt.exists(tid => m.homeTeamId == tid || m.awayTeamId == tid)
                  div(
                    cls := "p-2 rounded bg-gray-100 dark:bg-gray-700 flex justify-between items-center",
                    span(
                      cls := "flex items-center gap-2",
                      input(
                        typ := "checkbox",
                        cls := "rounded",
                        checked <-- exportSelectedMatchIds.signal.map(_.contains(m.id)),
                        onChange --> { _ =>
                          exportSelectedMatchIds.update(s => if (s.contains(m.id)) s - m.id else s + m.id)
                        }
                      ),
                      span(s"MD${m.matchday} ")
                    ),
                    span(span(home), span(cls := "font-medium", score), span(away)),
                    span(
                      cls := "flex gap-2 items-center",
                      button(
                        cls := "text-sm text-blue-600 dark:text-blue-400 hover:underline",
                        "Szczegóły",
                        onClick --> { _ => AppState.selectedMatchId.set(Some(m.id)) }
                      ),
                      if (isMyMatch && m.status == "Scheduled") {
                        button(
                          cls := "text-sm px-2 py-0.5 bg-amber-600 text-white rounded hover:bg-amber-700",
                          "Ustaw skład",
                          onClick --> { _ => myTeamIdOpt.foreach(tid => AppState.lineupContext.set(Some((m.id, tid)))) }
                        )
                      } else emptyNode,
                      span(cls := "text-gray-500 text-sm", m.status)
                    )
                  )
                }
                val listNodes = nodes.toSeq
                val moreFromListButton = if (showMoreFromList)
                  div(
                    button(
                      cls := "mt-2 mr-2 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-600 rounded hover:bg-gray-300",
                      s"Pokaż kolejne 15 z listy (${list.size - visibleCount} pozostałych)",
                      onClick --> { _ => visibleFixturesCount.update(n => (n + 15).min(list.size)) }
                    )
                  )
                else emptyNode
                val moreFromApiButton = if (showMoreFromApi)
                  div(
                    button(
                      cls := "mt-2 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-600 rounded hover:bg-gray-300",
                      "Załaduj więcej meczów z serwera",
                      onClick --> { _ => loadMoreFixtures() }
                    )
                  )
                else if (loadingMore) div(cls := "mt-2 text-sm text-gray-500", "Ładowanie...")
                else emptyNode
                (listNodes :+ moreFromListButton :+ moreFromApiButton).toSeq
              }
            ),
            h2(cls := "text-lg font-semibold mt-6 mb-2", "Okna transferowe"),
            p(cls := "text-xs text-gray-500 dark:text-gray-400 mb-1", "W okresie „Open” możesz składać oferty i otrzymywać oferty od botów."),
            div(
              cls := "space-y-2 mb-4",
              children <-- transferWindows.signal.map { wList =>
                wList.map { w =>
                  div(cls := "p-2 rounded bg-gray-100 dark:bg-gray-700 text-sm", s"Po MD${w.openAfterMatchday} – przed MD${w.closeBeforeMatchday}: ${w.status}")
                }
              }
            ),
            h3(cls := "font-medium mb-1", "Oferty transferowe"),
            p(cls := "text-xs text-gray-500 dark:text-gray-400 mb-2", "Kupujący → Sprzedawca. Gdy jesteś sprzedawcą (oferta dotyczy Twojego zawodnika), możesz zaakceptować lub odrzucić."),
            div(
              cls := "space-y-2 text-sm",
              children <-- transferOffers.signal.combineWith(teams.signal).combineWith(AppState.currentUser.signal).map { case (oList, ts, userOpt) =>
                val myTeamIdOpt = userOpt.flatMap(u => ts.find(_.ownerUserId.contains(u.id)).map(_.id))
                def name(dto: TransferOfferDto, from: Boolean): String =
                  if (from) dto.fromTeamName.getOrElse(ts.find(_.id == dto.fromTeamId).map(_.name).getOrElse(dto.fromTeamId))
                  else dto.toTeamName.getOrElse(ts.find(_.id == dto.toTeamId).map(_.name).getOrElse(dto.toTeamId))
                def playerLabel(dto: TransferOfferDto): String =
                  dto.playerName.getOrElse(dto.playerId.take(8) + "...")
                def fmtAmount(a: Double): String = f"$a%.0f"
                def statusLabel(s: String): String = s match {
                  case "Pending" => "Oczekuje"
                  case "Accepted" => "Zaakceptowano"
                  case "Rejected" => "Odrzucono"
                  case other => other
                }
                if (oList.isEmpty) List(div(cls := "text-gray-500 p-2", "Brak ofert w tej lidze."))
                else oList.map { o =>
                  val fromName = name(o, from = true)
                  val toName = name(o, from = false)
                  val iAmSeller = myTeamIdOpt.contains(o.toTeamId)
                  div(
                    cls := "p-3 rounded border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 flex flex-wrap justify-between items-center gap-2",
                    div(
                      cls := "flex flex-col gap-0.5",
                      span(cls := "font-medium", s"${fromName} → ${toName}"),
                      span(cls := "text-gray-600 dark:text-gray-300", s"Zawodnik: ${playerLabel(o)} · Kwota: ${fmtAmount(o.amount)} zł"),
                      span(cls := "text-xs", statusLabel(o.status))
                    ),
                    if (iAmSeller && o.status == "Pending") span(
                      cls := "flex gap-2",
                      button(cls := "px-2 py-1 text-green-700 dark:text-green-400 border border-green-500 rounded hover:bg-green-100 dark:hover:bg-green-900/30", "Akceptuj", onClick --> { _ => doAcceptOffer(o.id) }),
                      button(cls := "px-2 py-1 text-red-700 dark:text-red-400 border border-red-500 rounded hover:bg-red-100 dark:hover:bg-red-900/30", "Odrzuć", onClick --> { _ => doRejectOffer(o.id) })
                    ) else emptyNode
                  )
                }
              }
            ),
            h3(cls := "font-medium mt-4 mb-2", "Złóż ofertę (kupno zawodnika)"),
            child <-- newOfferError.signal.map {
              case Some(m) => div(cls := "text-red-600 text-sm mb-2", m)
              case None   => emptyNode
            },
            p(cls := "text-xs text-gray-500 dark:text-gray-400 mb-2", "Wybierz okno transferowe, drużynę (sprzedawcę), zawodnika i kwotę."),
            div(
              cls := "grid grid-cols-2 gap-2 mb-2 text-sm",
              div(
                label(cls := "block", "Okno transferowe"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  option(value := "", "— wybierz —"),
                  children <-- transferWindows.signal.map { wList =>
                    wList.filter(_.status == "Open").map(w => option(value := w.id, s"MD${w.openAfterMatchday}-${w.closeBeforeMatchday}"))
                  },
                  onChange --> { e => newOfferWindowId.set(e.target.asInstanceOf[org.scalajs.dom.html.Select].value) }
                )
              ),
              div(
                label(cls := "block", "Drużyna (sprzedawca)"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  option(value := "", "— wybierz —"),
                  children <-- teams.signal.combineWith(AppState.currentUser.signal).map { case (ts, userOpt) =>
                    val myId = userOpt.flatMap(u => ts.find(_.ownerUserId.contains(u.id)).map(_.id))
                    ts.filter(t => myId.fold(true)(_ != t.id)).map(t => option(value := t.id, t.name))
                  },
                  onChange --> { e =>
                    val v = e.target.asInstanceOf[org.scalajs.dom.html.Select].value
                    newOfferToTeamId.set(v)
                    newOfferPlayerId.set("")
                    if (v.nonEmpty)
                      App.runZio(ApiClient.getTeamPlayers(v)) {
                        case Right(p) => selectedTeamPlayers.set(p)
                        case Left(_)  => selectedTeamPlayers.set(Nil)
                      }
                    else selectedTeamPlayers.set(Nil)
                  }
                )
              )
            ),
            div(
              cls := "grid grid-cols-2 gap-2 mb-2 text-sm",
              div(
                label(cls := "block", "Zawodnik"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  option(value := "", "— wybierz drużynę —"),
                  children <-- selectedTeamPlayers.signal.map { pl =>
                    (option(value := "", "— wybierz —") +: pl.map(p => option(value := p.id, s"${p.firstName} ${p.lastName}"))).toSeq
                  },
                  onChange --> { e => newOfferPlayerId.set(e.target.asInstanceOf[org.scalajs.dom.html.Select].value) }
                )
              ),
              div(
                label(cls := "block", "Kwota"),
                input(
                  typ := "number",
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  placeholder := "0",
                  controlled(value <-- newOfferAmount.signal, onInput.mapToValue --> newOfferAmount.writer)
                )
              )
            ),
            button(
              cls := "px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 text-sm",
              disabled <-- newOfferBusy.signal,
              "Złóż ofertę",
              onClick --> { _ => doCreateOffer() }
            )
            ,
            h2(cls := "text-lg font-semibold mt-8 mb-2", "Scouting / Rekrutacja (MVP)"),
            child <-- (teams.signal.combineWith(AppState.currentUser.signal)).map { case (ts, userOpt) =>
              val teamIdOpt = myTeamIdOpt(ts, userOpt)
              teamIdOpt.foreach(refreshShortlist)
              div(
                cls := "p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-sm space-y-2",
                div(cls := "grid grid-cols-1 md:grid-cols-3 gap-2",
                  input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "Pozycja (np. ST, CB)",
                    controlled(value <-- scoutingPos.signal, onInput.mapToValue --> scoutingPos.writer)),
                  input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "Min overall (np. 12)",
                    controlled(value <-- scoutingMinOverall.signal, onInput.mapToValue --> scoutingMinOverall.writer)),
                  input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "Szukaj (nazwisko/drużyna)",
                    controlled(value <-- scoutingQ.signal, onInput.mapToValue --> scoutingQ.writer))
                ),
                button(
                  cls := "px-3 py-1 bg-blue-600 text-white rounded w-fit disabled:opacity-50",
                  disabled <-- scoutingBusy.signal,
                  "Szukaj",
                  onClick --> { _ => refreshScouting() }
                ),
                child <-- scoutingErr.signal.map(_.fold(emptyNode)(m => div(cls := "text-red-600 text-sm", m))),
                div(cls := "overflow-x-auto",
                  table(
                    cls := "w-full border-collapse border border-gray-300 dark:border-gray-600 text-sm",
                    thead(tr(
                      th(cls := "border p-2 text-left", "Zawodnik"),
                      th(cls := "border p-2 text-left", "Drużyna"),
                      th(cls := "border p-2", "Pozycje"),
                      th(cls := "border p-2", "Overall"),
                      th(cls := "border p-2", "Akcje")
                    )),
                    tbody(
                      children <-- scoutingList.signal.map { list =>
                        list.take(60).map { r =>
                          tr(
                            td(cls := "border p-2", r.playerName),
                            td(cls := "border p-2", r.teamName),
                            td(cls := "border p-2", r.preferredPositions.mkString(", ")),
                            td(cls := "border p-2 text-center", f"${r.overall}%.2f"),
                            td(cls := "border p-2 text-center",
                              teamIdOpt.fold(emptyNode) { tid =>
                                button(
                                  cls := "text-sm text-blue-600 dark:text-blue-400 hover:underline",
                                  "Dodaj do shortlisty",
                                  onClick --> { _ =>
                                    AppState.token.now().foreach { tok =>
                                      App.runZio(ApiClient.addToShortlist(tok, tid, r.playerId)) { _ => refreshShortlist(tid) }
                                    }
                                  }
                                )
                              }
                            )
                          )
                        }.toSeq
                      }
                    )
                  )
                ),
                h3(cls := "font-semibold mt-4", "Shortlista"),
                child <-- shortlist.signal.map { s =>
                  if (s.isEmpty) div(cls := "text-gray-500", "Pusta")
                  else ul(cls := "space-y-1", s.take(30).map(e => li(s"${e.playerName} (${e.fromTeamName})")).toSeq)
                },
                h3(cls := "font-semibold mt-4", "Raport skauta"),
                teamIdOpt.fold(div(cls := "text-gray-500", "Brak drużyny")) { tid =>
                  div(
                    cls := "space-y-2",
                    div(
                      cls := "grid grid-cols-1 md:grid-cols-3 gap-2",
                      input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "playerId",
                        controlled(value <-- reportTarget.signal, onInput.mapToValue --> reportTarget.writer)),
                      input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "rating 0-10",
                        controlled(value <-- reportRating.signal, onInput.mapToValue --> reportRating.writer)),
                      input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "notatki",
                        controlled(value <-- reportNotes.signal, onInput.mapToValue --> reportNotes.writer))
                    ),
                    button(
                      cls := "px-3 py-1 bg-green-600 text-white rounded w-fit disabled:opacity-50",
                      disabled <-- reportBusy.signal,
                      "Zapisz raport",
                      onClick --> { _ =>
                        AppState.token.now().foreach { tok =>
                          reportBusy.set(true)
                          App.runZio(ApiClient.createScoutingReport(tok, tid, CreateScoutingReportRequest(reportTarget.now(), reportRating.now().toDoubleOption.getOrElse(7.0), reportNotes.now()))) {
                            case Right(_) => reportBusy.set(false); reportTarget.set(""); reportNotes.set(""); refreshShortlist(tid)
                            case Left(_)  => reportBusy.set(false)
                          }
                        }
                      }
                    ),
                    child <-- reports.signal.map { rs =>
                      if (rs.isEmpty) div(cls := "text-gray-500", "Brak raportów")
                      else ul(cls := "space-y-1", rs.take(20).map(r => li(s"${r.playerName} – ${r.rating}: ${r.notes.take(80)}")).toSeq)
                    }
                  )
                }
              )
            },
          )
          }
        case None => div(cls := "text-gray-500", "Loading...")
      }
    )
  }
}
