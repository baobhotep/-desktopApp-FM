package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*

object MatchDetailPage {
  val eventTypeFilterOptions = "Wszystkie" +: List("KickOff", "Pass", "LongPass", "Cross", "PassIntercepted", "Dribble", "DribbleLost", "Shot", "Goal", "Foul", "YellowCard", "RedCard", "Injury", "Substitution", "Corner", "ThrowIn", "FreeKick", "Offside")

  val LogPageSize = 50

  /** Inferuje etykietę formacji z listy slotów (np. 4-3-3) na podstawie pozycji. */
  def inferFormationFromSlots(slots: List[String]): String = {
    val defSlots = Set("LB", "RB", "LCB", "RCB", "CB")
    val midSlots = Set("LM", "RM", "LCM", "RCM", "CM", "LDM", "RDM", "CDM", "CAM", "LWB", "RWB")
    val fwdSlots = Set("LW", "RW", "ST", "CF", "LST", "RST")
    val defCount = slots.count(defSlots.contains)
    val midCount = slots.count(midSlots.contains)
    val fwdCount = slots.count(fwdSlots.contains)
    if (defCount + midCount + fwdCount >= 10) s"$defCount-$midCount-$fwdCount" else "—"
  }

  private def buildPerPlayerTable(
    s: MatchSummaryDto,
    sideFilter: String,
    homePlayerIds: Set[String],
    awayPlayerIds: Set[String]
  ): Element = {
    val allPids = (s.vaepBreakdownByPlayer.getOrElse(Map.empty).keys ++ s.iwpByPlayer.getOrElse(Map.empty).keys ++
      s.estimatedDistanceByPlayer.getOrElse(Map.empty).keys ++ s.metabolicLoadByPlayer.getOrElse(Map.empty).keys ++
      s.playerTortuosityByPlayer.getOrElse(Map.empty).keys ++ s.influenceScoreByPlayer.getOrElse(Map.empty).keys).toSet
    val pids = (sideFilter match {
      case "home" => allPids.intersect(homePlayerIds)
      case "away" => allPids.intersect(awayPlayerIds)
      case _      => allPids
    }).toList
    if (pids.isEmpty) div()
    else {
      val vaep = s.vaepBreakdownByPlayer.getOrElse(Map.empty).view.mapValues(_.values.sum).toMap
      val iwp = s.iwpByPlayer.getOrElse(Map.empty)
      val dist = s.estimatedDistanceByPlayer.getOrElse(Map.empty)
      val metabolic = s.metabolicLoadByPlayer.getOrElse(Map.empty)
      val tort = s.playerTortuosityByPlayer.getOrElse(Map.empty)
      val influence = s.influenceScoreByPlayer.getOrElse(Map.empty)
      val rows = pids.map { pid =>
        (pid, vaep.getOrElse(pid, 0.0), iwp.getOrElse(pid, 0.0), dist.getOrElse(pid, 0.0), metabolic.getOrElse(pid, 0.0), tort.getOrElse(pid, 0.0), influence.getOrElse(pid, 0.0))
      }.sortBy(-_._2).take(12)
      div(
        cls := "mt-2 overflow-x-auto",
        p(cls := "text-xs font-medium mb-1", "Per zawodnik (VAEP, IWP, dystans m, metabolic m, tortuosity, influence):"),
        table(
          cls := "text-xs border border-slate-300 dark:border-slate-600 w-full",
          thead(
            tr(
              th(cls := "border-b px-1 text-left", "ID"),
              th(cls := "border-b px-1 text-right", "VAEP"),
              th(cls := "border-b px-1 text-right", "IWP"),
              th(cls := "border-b px-1 text-right", "m"),
              th(cls := "border-b px-1 text-right", "met"),
              th(cls := "border-b px-1 text-right", "tort"),
              th(cls := "border-b px-1 text-right", "infl")
            )
          ),
          tbody(
            rows.map { case (pid, v, i, d, m, t, inf) =>
              tr(
                td(cls := "border-b px-1", pid.take(8)),
                td(cls := "border-b px-1 text-right", f"$v%.2f"),
                td(cls := "border-b px-1 text-right", f"$i%.2f"),
                td(cls := "border-b px-1 text-right", f"$d%.0f"),
                td(cls := "border-b px-1 text-right", f"$m%.0f"),
                td(cls := "border-b px-1 text-right", f"$t%.2f"),
                td(cls := "border-b px-1 text-right", f"$inf%.2f")
              )
            }: _*
          )
        )
      )
    }
  }

  private def buildXgTimeline(matchDto: MatchDto, log: MatchLogDto): Element = {
    val homeId = matchDto.homeTeamId
    val awayId = matchDto.awayTeamId
    val shotEvents = log.events.filter(e =>
      (e.eventType == "Shot" || e.eventType == "Goal" || e.eventType == "Penalty") &&
        e.metadata.get("xG").flatMap(str => str.toDoubleOption).nonEmpty &&
        e.teamId.nonEmpty
    )
    if (shotEvents.isEmpty) div()
    else {
      val sorted = shotEvents.sortBy(_.minute)
      val checkpoints = List(15, 30, 45, 60, 75, 90)
      var idx = 0
      var xgHome = 0.0
      var xgAway = 0.0
      val rows = checkpoints.map { stopMin =>
        while (idx < sorted.length && sorted(idx).minute <= stopMin) {
          val e = sorted(idx)
          val xg = e.metadata.get("xG").flatMap(str => str.toDoubleOption).getOrElse(0.0)
          e.teamId.foreach { tid =>
            if (tid == homeId) xgHome += xg
            else if (tid == awayId) xgAway += xg
          }
          idx += 1
        }
        (stopMin, xgHome, xgAway)
      }
      val line = rows.map { case (m, h, a) => f"$m%2d': ${h}%.2f–${a}%.2f" }.mkString(" | ")
      val maxXg = (rows.flatMap { case (_, h, a) => List(h, a) }.maxOption.getOrElse(0.01)).max(0.01)
      val barMaxPx = 28
      val timelineBars = checkpoints.zip(rows).map { case (min, (_, h, a)) =>
        val hPx = (h / maxXg * barMaxPx).min(barMaxPx).max(if (h > 0) 2 else 0)
        val aPx = (a / maxXg * barMaxPx).min(barMaxPx).max(if (a > 0) 2 else 0)
        div(
          cls := "flex-1 flex flex-col items-center gap-0.5",
          div(cls := "w-full flex flex-col-reverse gap-px", styleAttr := s"height: ${barMaxPx * 2}px",
            div(styleAttr := s"height: ${hPx}px; background: #3b82f6; border-radius: 2px 2px 0 0"),
            div(styleAttr := s"height: ${aPx}px; background: #6b7280; border-radius: 2px 2px 0 0")
          ),
          span(cls := "text-gray-500", s"${min}'")
        )
      }
      val timelineMods = (cls := "flex gap-1 items-end text-xs") +: (title := "Słupki: skumulowane xG gospodarzy (niebieski) i gości (szary) co 15 min") +: timelineBars
      div(
        cls := "space-y-1",
        p(span("Timeline xG (co 15'): "), span(cls := "font-mono text-xs", line)),
        div(timelineMods: _*)
      )
    }
  }

  /** Wykres słupkowy VAEP per zawodnik (poziome paski, max 12). */
  private def buildVaepBarChart(vaepByPlayer: Map[String, Double]): Element = {
    val sorted = vaepByPlayer.toList.sortBy(-_._2).take(12)
    if (sorted.isEmpty) div()
    else {
      val maxV = sorted.map(_._2).maxOption.filter(_ > 0).getOrElse(0.01)
      val bars = sorted.map { case (pid, v) =>
        val pct = (v / maxV * 100).min(100)
        div(
          cls := "flex items-center gap-2",
          span(cls := "text-xs w-20 truncate", pid.take(8)),
          div(cls := "flex-1 h-4 bg-gray-200 dark:bg-gray-600 rounded overflow-hidden",
            div(styleAttr := s"width: $pct%; height: 100%; background: #10b981; border-radius: 0 2px 2px 0")
          ),
          span(cls := "text-xs tabular-nums", f"$v%.2f")
        )
      }
      val spaceAndBars = (cls := "space-y-1") +: bars
      div(
        cls := "space-y-1 mt-2",
        p(cls := "text-xs font-medium", "VAEP per zawodnik (wykres):"),
        div(spaceAndBars: _*)
      )
    }
  }

  /** Possession w czasie: udział gospodarzy w zdarzeniach (przybliżenie posiadania) co 15 min. */
  private def buildPossessionByTime(matchDto: MatchDto, log: MatchLogDto): Element = {
    val homeId = matchDto.homeTeamId
    val awayId = matchDto.awayTeamId
    val withTeam = log.events.filter(e => e.teamId.nonEmpty && (e.teamId.get == homeId || e.teamId.get == awayId))
    if (withTeam.isEmpty) div()
    else {
      val checkpoints = List(15, 30, 45, 60, 75, 90)
      val segments = checkpoints.map { stopMin =>
        val startMin = checkpoints.indexOf(stopMin) match { case i if i > 0 => checkpoints(i - 1); case _ => 0 }
        val inSegment = withTeam.filter(e => e.minute > startMin && e.minute <= stopMin)
        val home = inSegment.count(_.teamId.contains(homeId))
        val away = inSegment.count(_.teamId.contains(awayId))
        val total = home + away
        val homePct = if (total > 0) (home * 100.0 / total).toInt else 50
        (stopMin, homePct, 100 - homePct)
      }
      val line = segments.map { case (m, h, a) => s"${m}' ${h}%–${a}%" }.mkString(" | ")
      div(
        cls := "mt-2",
        p(cls := "text-xs font-medium", "Posiadanie w czasie (udział zdarzeń co 15'):"),
        p(cls := "text-xs text-gray-600 dark:text-gray-400", line)
      )
    }
  }

  /** Press by zone: liczba akcji defensywnych (Tackle, PassIntercepted, DribbleLost) per strefa 1–12. */
  private def buildPressByZone(matchDto: MatchDto, log: MatchLogDto): Element = {
    val homeId = matchDto.homeTeamId
    val awayId = matchDto.awayTeamId
    val defensiveTypes = Set("Tackle", "PassIntercepted", "DribbleLost")
    val defensive = log.events.filter(e => defensiveTypes.contains(e.eventType) && e.zone.exists(z => z >= 1 && z <= 12))
    if (defensive.isEmpty) div()
    else {
      val byZoneHome = (1 to 12).map(z => z -> defensive.count(e => e.zone.contains(z) && e.teamId.contains(homeId))).toMap
      val byZoneAway = (1 to 12).map(z => z -> defensive.count(e => e.zone.contains(z) && e.teamId.contains(awayId))).toMap
      val maxV = (byZoneHome.values ++ byZoneAway.values).maxOption.getOrElse(1)
      val bars = (1 to 12).map { z =>
            val h = byZoneHome.getOrElse(z, 0)
            val a = byZoneAway.getOrElse(z, 0)
            val hPct = if (maxV > 0) (h.toDouble / maxV * 20).max(2) else 2
            val aPct = if (maxV > 0) (a.toDouble / maxV * 20).max(2) else 2
            div(cls := "flex-1 min-w-[24px] flex flex-col items-center", title := s"Strefa $z: gosp. $h, goście $a",
              div(cls := "w-full flex gap-px flex-col-reverse", styleAttr := "height: 40px",
                div(styleAttr := s"height: ${hPct}px; background: #3b82f6; border-radius: 1px"),
                div(styleAttr := s"height: ${aPct}px; background: #6b7280; border-radius: 1px")
              ),
              span(cls := "text-xs text-gray-500", s"$z"))
          }.toSeq
      val innerMods = (cls := "flex gap-0.5 items-end flex-wrap") +: bars
      div(
        cls := "mt-2",
        p(cls := "text-xs font-medium", "Pressing wg stref (akcje defensywne: Tackle, Przechwyt, DribbleLost):"),
        div(innerMods: _*)
      )
    }
  }

  /** Wykres słupkowy EPV/xT per strefa (1–12). */
  private def buildXtByZoneBarChart(xtByZone: List[Double]): Element = {
    if (xtByZone.size != 12) div()
    else {
      val maxV = xtByZone.maxOption.filter(_ > 0).getOrElse(0.01)
      val bars = xtByZone.zipWithIndex.map { case (v, i) =>
        val pct = (v / maxV * 100).min(100)
        div(
          cls := "flex-1 flex flex-col items-center",
          div(styleAttr := s"height: ${(pct / 100.0 * 60).max(4)}px; width: 100%; background: #8b5cf6; border-radius: 2px 2px 0 0", title := f"Strefa ${i + 1}: $v%.3f"),
          span(cls := "text-xs text-gray-500", s"${i + 1}")
        )
      }
      val flexAndBars = (cls := "flex gap-0.5 items-end") +: bars
      div(
        cls := "space-y-1 mt-2",
        p(cls := "text-xs font-medium", "EPV/xT strefy 1–12 (wykres):"),
        div(flexAndBars: _*)
      )
    }
  }

  def render(matchId: String, goBack: () => Unit): Element = {
    val matchData = Var[Option[MatchDto]](None)
    val logData = Var[Option[MatchLogDto]](None)
    val teams = Var[Map[String, String]](Map.empty)
    val teamsList = Var[List[TeamDto]](Nil)
    val squads = Var[Option[List[MatchSquadDto]]](None)
    val squadsError = Var[Option[String]](None)
    val error = Var[Option[String]](None)
    val eventTypeFilter = Var("Wszystkie")
    val logLoadingMore = Var(false)
    val pressTone = Var("praise")
    val pressResultVar = Var[Option[String]](None)
    val playerTeamFilter = Var("all") // "all" | "home" | "away"
    val exportJson = Var[Option[String]](None)
    val exportJsonError = Var[Option[String]](None)
    val exportJsonBusy = Var(false)
    /** Zakładka: "summary" | "advanced" | "events" */
    val matchDetailTab = Var("summary")
    /** Limit wyświetlanych zdarzeń (wirtualizacja – pokaż kolejne 50). */
    val visibleEventCount = Var(50)

    def loadLog(offset: Int): Unit =
      App.runZio(ApiClient.getMatchLog(matchId, Some(LogPageSize), Some(offset))) {
        case Right(newLog) =>
          logData.update {
            case Some(existing) =>
              Some(existing.copy(events = existing.events ++ newLog.events, total = newLog.total.orElse(existing.total), matchReport = newLog.matchReport.orElse(existing.matchReport)))
            case None => Some(newLog)
          }
          logLoadingMore.set(false)
        case Left(_) => logLoadingMore.set(false)
      }

    def exportAnalyticsJson(): Unit =
      AppState.token.now() match {
        case None =>
          exportJsonError.set(Some("Zaloguj się, aby pobrać analitykę (JSON)."))
        case Some(tok) =>
          exportJsonBusy.set(true)
          exportJsonError.set(None)
          exportJson.set(None)
          App.runZio(ApiClient.exportMatchLogs(tok, List(matchId), "json-full")) {
            case Right(text) =>
              exportJsonBusy.set(false)
              exportJson.set(Some(text))
            case Left(msg) =>
              exportJsonBusy.set(false)
              exportJsonError.set(Some(msg))
          }
      }

    def load(): Unit = {
      App.runZio(ApiClient.getMatch(matchId)) {
        case Right(m) =>
          matchData.set(Some(m))
          App.runZio(ApiClient.getTeams(m.leagueId)) {
            case Right(ts) =>
              teams.set(ts.map(t => t.id -> t.name).toMap)
              teamsList.set(ts)
            case Left(_)  =>
          }
          AppState.token.now().foreach { tok =>
            App.runZio(ApiClient.getMatchSquads(tok, matchId)) {
              case Right(sq) =>
                squads.set(Some(sq))
                squadsError.set(None)
              case Left(msg) =>
                squads.set(None)
                squadsError.set(Some(msg))
            }
          }
          if (m.status == "Played") {
            logData.set(None)
            App.runZio(ApiClient.getMatchLog(matchId, Some(LogPageSize), Some(0))) {
              case Right(log) => logData.set(Some(log))
              case Left(_)    =>
            }
          }
        case Left(m) => error.set(Some(m))
      }
    }
    load()

    div(
      cls := "max-w-2xl mx-auto",
      button(
        cls := "mb-4 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-700 rounded hover:bg-gray-300",
        "← Wstecz",
        onClick --> { _ => goBack() }
      ),
      child <-- error.signal.map {
        case Some(m) => div(cls := "text-red-600 mb-4", m)
        case None    => emptyNode
      },
      child <-- matchData.signal.map {
        case Some(m) =>
          val homeName = teams.now().getOrElse(m.homeTeamId, m.homeTeamId)
          val awayName = teams.now().getOrElse(m.awayTeamId, m.awayTeamId)
          val scoreStr = (m.homeGoals, m.awayGoals) match {
            case (Some(h), Some(a)) => s"$h – $a"
            case _                  => "vs"
          }
          val refereeLine = m.refereeName.filter(_.nonEmpty).orElse(Some(m.refereeId).filter(_.nonEmpty)).fold("")(n => s" · Sędzia: $n")
          div(
            h1(cls := "text-xl font-bold mb-2", s"Mecz kolejki ${m.matchday}"),
            div(
              cls := "p-4 rounded bg-gray-100 dark:bg-gray-700 mb-4 flex justify-between items-center",
              span(homeName),
              span(cls := "text-lg font-bold", scoreStr),
              span(awayName)
            ),
            p(cls := "text-sm text-gray-500 mb-4", s"Status: ${m.status}$refereeLine"),
            div(
              cls := "mb-4 flex flex-col gap-1",
              button(
                cls := "px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50",
                "Pobierz analitykę (JSON)",
                disabled <-- exportJsonBusy.signal,
                onClick --> { _ => exportAnalyticsJson() }
              ),
              child <-- exportJsonError.signal.map {
                case Some(msg) => div(cls := "text-xs text-red-500", msg)
                case None      => emptyNode
              },
              child <-- exportJson.signal.map {
                case Some(text) =>
                  div(
                    p(cls := "text-xs text-gray-500", "JSON (format json-full, 1 mecz):"),
                    pre(cls := "whitespace-pre-wrap break-all text-[11px] bg-black/40 text-green-100 p-2 rounded max-h-40 overflow-y-auto", text)
                  )
                case None => emptyNode
              }
            ),
            child <-- (AppState.currentUser.signal.combineWith(teamsList.signal)).map { case (userOpt, ts) =>
              val myTeamIdOpt = userOpt.flatMap(u => ts.find(_.ownerUserId.contains(u.id)).map(_.id))
              if (m.status == "Scheduled" && myTeamIdOpt.exists(tid => m.homeTeamId == tid || m.awayTeamId == tid))
                button(
                  cls := "mb-4 px-4 py-2 bg-amber-600 text-white rounded hover:bg-amber-700",
                  "Ustaw skład",
                  onClick --> { _ => myTeamIdOpt.foreach(tid => AppState.lineupContext.set(Some((matchId, tid)))) }
                )
              else emptyNode
            },
            child <-- AppState.token.signal.combineWith(AppState.currentUser.signal).combineWith(teamsList.signal).map { x =>
              val tokOpt = x._1
              val userOpt = x._2
              val ts = x._3
              val myTeamIdOpt = Option.when(m.status == "Played" && tokOpt.nonEmpty)(()).flatMap(_ => userOpt.flatMap(u => ts.find(_.ownerUserId.contains(u.id)).map(_.id)))
              if (myTeamIdOpt.isEmpty) emptyNode
              else {
                val tid = myTeamIdOpt.get
                val tokenOpt = tokOpt
                div(
                  cls := "mb-4 p-3 rounded bg-gray-100 dark:bg-gray-700",
                  h3(cls := "font-medium mb-2", "Konferencja prasowa (po meczu)"),
                  p(cls := "text-xs text-gray-500 mb-2", "Ton wypowiedzi wpływa na morale drużyny."),
                  div(
                    cls := "flex flex-wrap items-center gap-2",
                    select(
                      cls := "px-2 py-1 border rounded dark:bg-gray-600 text-sm",
                      value <-- pressTone.signal,
                      onChange --> { e => pressTone.set(e.target.asInstanceOf[org.scalajs.dom.html.Select].value) },
                      option(value := "praise", "Pochwała"),
                      option(value := "criticize", "Krytyka"),
                      option(value := "calm", "Spokojnie")
                    ),
                    button(
                      cls := "px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700",
                      "Wyślij",
                      onClick --> { _ =>
                        pressResultVar.set(None)
                        tokenOpt.foreach { tok =>
                          App.runZio(ApiClient.submitPressConference(tok, matchId, tid, "post", pressTone.now())) {
                            case Right(_)  => pressResultVar.set(Some("Wysłano."))
                            case Left(msg) => pressResultVar.set(Some(s"Błąd: $msg"))
                          }
                        }
                      }
                    )
                  ),
                  child <-- pressResultVar.signal.map {
                    case Some(msg) => div(cls := "mt-2 text-sm", if (msg == "Wysłano.") span(cls := "text-green-600", msg) else span(cls := "text-red-600", msg))
                    case None      => emptyNode
                  }
                )
              }
            },
            child <-- squadsError.signal.map {
              case Some(msg) => div(cls := "text-xs text-red-500 mb-1", s"Błąd wczytywania składów: $msg")
              case None      => emptyNode
            },
            child <-- squads.signal.combineWith(teamsList.signal).map { case (sqListOpt, tsList) =>
              (sqListOpt, tsList) match {
                case (Some(sqList), ts) if sqList.nonEmpty =>
                  val byTeam = sqList.groupBy(_.teamId)
                  val isBot = (tid: String) => ts.exists(t => t.id == tid && t.ownerType == "Bot")
                  div(
                    cls := "mb-4 p-3 rounded bg-gray-100 dark:bg-gray-700 text-sm",
                    h3(cls := "font-medium mb-2", "Zapisane składy"),
                    byTeam.toList.sortBy(_._1).flatMap { case (tid, list) =>
                      val teamName = teams.now().getOrElse(tid, tid)
                      list.headOption.toList.map { s =>
                        val playersLine = s.lineup.map(slot => slot.playerId).mkString(", ")
                        val formationLabel = inferFormationFromSlots(s.lineup.map(_.positionSlot))
                        val formacjaLine = if (isBot(tid)) p(cls := "text-xs text-gray-500 mb-1", s"Formacja rywala: $formationLabel") else emptyNode
                        div(
                          cls := "mb-2",
                          p(cls := "font-semibold", teamName),
                          formacjaLine,
                          p(cls := "text-xs text-gray-500 mb-1", s"Źródło: ${s.source}"),
                          p(cls := "break-words", playersLine)
                        )
                      }
                    }
                  )
                case _ => emptyNode
              }
            },
            child <-- logData.signal.map {
              case Some(log) if log.matchReport.nonEmpty =>
                div(cls := "mb-4 p-3 rounded bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 text-sm", h3(cls := "font-medium mb-2", "Raport gazetowy"), p(cls := "whitespace-pre-wrap", log.matchReport.get))
              case _ => emptyNode
            },
            div(
              cls := "flex gap-2 mb-3 flex-wrap",
              button(
                cls <-- matchDetailTab.signal.map(t => if (t == "summary") "px-3 py-1.5 text-sm rounded border dark:border-gray-600 bg-blue-600 text-white border-blue-600" else "px-3 py-1.5 text-sm rounded border dark:border-gray-600 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200"),
                "Podsumowanie",
                onClick --> { _ => matchDetailTab.set("summary") }
              ),
              button(
                cls <-- matchDetailTab.signal.map(t => if (t == "advanced") "px-3 py-1.5 text-sm rounded border dark:border-gray-600 bg-blue-600 text-white border-blue-600" else "px-3 py-1.5 text-sm rounded border dark:border-gray-600 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200"),
                "Analityka",
                onClick --> { _ => matchDetailTab.set("advanced") }
              ),
              button(
                cls <-- matchDetailTab.signal.map(t => if (t == "events") "px-3 py-1.5 text-sm rounded border dark:border-gray-600 bg-blue-600 text-white border-blue-600" else "px-3 py-1.5 text-sm rounded border dark:border-gray-600 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200"),
                "Zdarzenia",
                onClick --> { _ => matchDetailTab.set("events") }
              )
            ),
            div(
              styleAttr <-- matchDetailTab.signal.map(t => if (t == "summary") "display: block" else "display: none"),
              child <-- logData.signal.combineWith(playerTeamFilter.signal).map {
              case (Some(log), teamFilter) if log.summary.nonEmpty =>
                val s = log.summary.get
                val (ph, pa) = (s.possessionPercent.lift(0).getOrElse(0.0), s.possessionPercent.lift(1).getOrElse(0.0))
                val (sh, sa) = (s.shotsTotal.lift(0).getOrElse(0), s.shotsTotal.lift(1).getOrElse(0))
                val (xh, xa) = (s.xgTotal.lift(0).getOrElse(0.0), s.xgTotal.lift(1).getOrElse(0.0))
                val (fh, fa) = (s.fouls.lift(0).getOrElse(0), s.fouls.lift(1).getOrElse(0))
                val (ch, ca) = (s.corners.lift(0).getOrElse(0), s.corners.lift(1).getOrElse(0))
                val (pth, pta) = (s.passesTotal.lift(0).getOrElse(0), s.passesTotal.lift(1).getOrElse(0))
                val (pah, paa) = (s.passAccuracyPercent.lift(0).getOrElse(0.0), s.passAccuracyPercent.lift(1).getOrElse(0.0))
                val (yh, ya) = (s.yellowCards.lift(0).getOrElse(0), s.yellowCards.lift(1).getOrElse(0))
                val (rh, ra) = (s.redCards.lift(0).getOrElse(0), s.redCards.lift(1).getOrElse(0))
                val (crh, cra) = (s.crossesTotal.lift(0).getOrElse(0), s.crossesTotal.lift(1).getOrElse(0))
                val (lbh, lba) = (s.longBallsTotal.lift(0).getOrElse(0), s.longBallsTotal.lift(1).getOrElse(0))
                val (ih, ia) = (s.interceptions.lift(0).getOrElse(0), s.interceptions.lift(1).getOrElse(0))
                val (th, ta) = (s.throwIns.lift(0).getOrElse(0), s.throwIns.lift(1).getOrElse(0))
                val (fkh, fka) = (s.freeKicksWon.lift(0).getOrElse(0), s.freeKicksWon.lift(1).getOrElse(0))
                val (oh, oa) = (s.offsides.lift(0).getOrElse(0), s.offsides.lift(1).getOrElse(0))
                div(
                  div(
                    cls := "mb-4 p-3 rounded bg-gray-100 dark:bg-gray-700 text-sm space-y-1",
                    h3(cls := "font-medium mb-2", "Podsumowanie meczu"),
                    p(s"Posiadanie: ${ph.toInt}% – ${pa.toInt}%"),
                    p(s"Strzały: $sh – $sa · xG: ${f"$xh%.2f"} – ${f"$xa%.2f"}"),
                    p(s"Podania: $pth – $pta (celność ${pah.toInt}% – ${paa.toInt}%)"),
                    p(s"Dośrodkowania: $crh – $cra · Długie piłki: $lbh – $lba"),
                    p(s"Przechwyty: $ih – $ia"),
                    p(s"Faule: $fh – $fa · Żółte: $yh – $ya · Czerwone: $rh – $ra"),
                    s.duelsWon.filter(_.size >= 2).fold(emptyNode)(list => p(s"Pojedynki wygrane: ${list(0)} – ${list(1)}")),
                    s.aerialDuelsWon.filter(_.size >= 2).fold(emptyNode)(list => p(s"Pojedynki w powietrzu: ${list(0)} – ${list(1)}")),
                    s.possessionLost.filter(_.size >= 2).fold(emptyNode)(list => p(s"Utracone piłki: ${list(0)} – ${list(1)}")),
                    s.wpaFinal.fold(emptyNode)(w => p(s"WPA (końcowe prawd. wygranej gosp.): ${(w * 100).toInt}%")),
                    s.injuries.filter(_.size >= 2).fold(emptyNode)(list => p(s"Kontuzje: ${list(0)} – ${list(1)}")),
                    p(s"Rożne: $ch – $ca · Wrzuty: $th – $ta · Rzuty wolne: $fkh – $fka · Spalone: $oh – $oa"),
                    s.setPiecePatternH.filter(_.nonEmpty).fold(emptyNode)(h => p(s"NMF stałe fragmenty (H, strefy): ${h.size} komponentów")),
                    if (s.fieldTilt.exists(_.size >= 2)) p(s"Field Tilt (tercja ataku): ${(s.fieldTilt.getOrElse(Nil).lift(0).getOrElse(0.0) * 100).toInt}% – ${(s.fieldTilt.getOrElse(Nil).lift(1).getOrElse(0.0) * 100).toInt}%") else emptyNode,
                    if (s.ppda.exists(_.size >= 2)) p(s"PPDA: ${f"${s.ppda.getOrElse(Nil).lift(0).getOrElse(0.0)}%.1f"} – ${f"${s.ppda.getOrElse(Nil).lift(1).getOrElse(0.0)}%.1f"}") else emptyNode,
                    s.vaepTotal.filter(_.size >= 2).fold(emptyNode)(v => p(s"OBV (VAEP): ${f"${v.lift(0).getOrElse(0.0)}%.2f"} – ${f"${v.lift(1).getOrElse(0.0)}%.2f"}"))
                  ),
                  log.possessionBySegment.filter(_.size == 6).fold(emptyNode) { seg =>
                    val labels = List("0–15'", "15–30'", "30–45'", "45–60'", "60–75'", "75–90'")
                    val bars = seg.zip(labels).map { case (homeShare, label) =>
                      val pct = (homeShare * 100).min(100).max(0)
                      div(
                        cls := "flex-1 flex flex-col items-center",
                        div(styleAttr := s"height: ${(pct / 100.0 * 40).max(2)}px; width: 100%; background: #3b82f6; border-radius: 2px 2px 0 0", title := f"$pct%.0f%%"),
                        span(cls := "text-xs text-gray-500 mt-0.5", label)
                      )
                    }
                    val mods = (cls := "flex gap-1 items-end") +: bars
                    div(
                      cls := "mb-4 p-3 rounded bg-slate-100 dark:bg-slate-800 text-sm",
                      h3(cls := "font-medium mb-2", "Posiadanie w czasie (udział gosp. per 15 min)"),
                      div(mods: _*)
                    )
                  },
                  (log.pressByZoneHome.filter(_.size == 12), log.pressByZoneAway.filter(_.size == 12)) match {
                    case (Some(homeZ), Some(awayZ)) =>
                      val maxV = (homeZ ++ awayZ).maxOption.filter(_ > 0).getOrElse(1)
                      val zoneBars = (1 to 12).toList.map { i =>
                        val h = homeZ(i - 1)
                        val a = awayZ(i - 1)
                        val hPx = (h.toDouble / maxV * 32).max(0).min(32)
                        val aPx = (a.toDouble / maxV * 32).max(0).min(32)
                        div(
                          cls := "flex-1 flex flex-col items-center",
                          div(cls := "w-full flex flex-col-reverse gap-px", styleAttr := "height: 36px",
                            div(styleAttr := s"height: ${hPx}px; background: #3b82f6; border-radius: 2px 2px 0 0"),
                            div(styleAttr := s"height: ${aPx}px; background: #6b7280; border-radius: 2px 2px 0 0")
                          ),
                          span(cls := "text-xs text-gray-500", s"$i")
                        )
                      }
                      val zMods = (cls := "flex gap-0.5 items-end") +: zoneBars
                      div(
                        cls := "mb-4 p-3 rounded bg-slate-100 dark:bg-slate-800 text-sm",
                        h3(cls := "font-medium mb-2", "Pressing per strefa (akcje defensywne: Tackle, Przechwyt)"),
                        p(cls := "text-xs text-gray-500 mb-1", "Gosp. (niebieski) vs goście (szary)"),
                        div(zMods: _*)
                      )
                    case _ => emptyNode
                  }
                )
              case _ => emptyNode
            }
            ),
            div(
              styleAttr <-- matchDetailTab.signal.map(t => if (t == "advanced") "display: block" else "display: none"),
              child <-- logData.signal.map {
              case Some(log) if log.summary.nonEmpty =>
                val s = log.summary.get
                val hasAdvanced = s.ballTortuosity.nonEmpty || s.metabolicLoad.nonEmpty || s.xtByZone.nonEmpty || s.homeShareByZone.nonEmpty ||
                  s.pressingByPlayer.nonEmpty || s.estimatedDistanceByPlayer.nonEmpty || s.vaepBreakdownByPlayer.nonEmpty || s.influenceByPlayer.nonEmpty ||
                  s.avgDefendersInConeByZone.nonEmpty || s.avgGkDistanceByZone.nonEmpty || s.setPieceZoneActivity.nonEmpty || s.pressingInOppHalfByPlayer.nonEmpty ||
                  s.playerTortuosityByPlayer.nonEmpty || s.metabolicLoadByPlayer.nonEmpty || s.iwpByPlayer.nonEmpty ||
                  s.setPiecePatternW.nonEmpty || s.setPieceRoutineCluster.nonEmpty || s.poissonPrognosis.nonEmpty ||
                  s.voronoiCentroidByZone.nonEmpty || s.passValueByPlayer.nonEmpty || s.passValueTotal.nonEmpty || s.passValueUnderPressureTotal.nonEmpty || s.passValueUnderPressureByPlayer.nonEmpty || s.influenceScoreByPlayer.nonEmpty
                if (!hasAdvanced) emptyNode
                else {
                  val squadsOpt = squads.now()
                  val (homePlayerIds, awayPlayerIds) = squadsOpt match {
                    case Some(list) =>
                      val homeIds = list.find(_.teamId == m.homeTeamId).map(_.lineup.map(_.playerId).toSet).getOrElse(Set.empty[String])
                      val awayIds = list.find(_.teamId == m.awayTeamId).map(_.lineup.map(_.playerId).toSet).getOrElse(Set.empty[String])
                      (homeIds, awayIds)
                    case None => (Set.empty[String], Set.empty[String])
                  }
                  div(
                  cls := "mb-4 p-3 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-sm space-y-1",
                  h3(cls := "font-medium mb-2", "Analityka zaawansowana"),
                  p(cls := "text-xs text-slate-500", "EPV = Expected Possession Value (wartość stref). OBV w podsumowaniu = VAEP. Karne: mieszane strategie Nash (kierunek L/R)."),
                  s.ballTortuosity.fold(emptyNode)(t => p(s"Tortuosity ścieżki piłki (gBRI): ${f"$t%.2f"}")),
                  s.metabolicLoad.fold(emptyNode)(m => p(s"Metabolic load (przybł.): ${f"$m%.0f"} m")),
                  s.xtByZone.filter(_.size == 12).fold(emptyNode)(xt => {
                    val maxV = xt.maxOption.getOrElse(0.01)
                    val bars = xt.zipWithIndex.map { case (v, i) => s"${i + 1}:${"=" * (math.round(v / maxV * 8).toInt.min(20))} ${f"$v%.2f"}" }.mkString(" | ")
                    div(
                      p(span("EPV/xT strefy: "), span(cls := "font-mono text-xs", bars)),
                      buildXtByZoneBarChart(xt)
                    )
                  }),
                  s.homeShareByZone.filter(_.size == 12).fold(emptyNode)(vor => p(s"Voronoi (udział gosp. w strefach 1–12): ${vor.map(v => f"${v * 100}%.0f%%").mkString(", ")}")),
                  s.pressingByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"Pressing (akcje defensywne): ${m.toList.sortBy(-_._2).take(5).map { case (pid, n) => s"$pid: $n" }.mkString(", ")}")),
                  s.estimatedDistanceByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"Dystans szac. (m): ${m.toList.sortBy(-_._2).take(5).map { case (pid, d) => s"$pid: ${f"$d%.0f"}" }.mkString(", ")}")),
                  s.vaepBreakdownByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => {
                    val lines = m.toList.take(8).map { case (pid, byType) =>
                      val top = byType.toList.sortBy(-_._2).take(3).map { case (et, v) => s"$et: ${f"$v%.2f"}" }.mkString(", ")
                      s"$pid: $top"
                    }.mkString("; ")
                    p(s"I-VAEP (rozbicie per typ zdarzenia): $lines")
                  }),
                  s.vaepBreakdownByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => {
                    val totalByPlayer = m.view.mapValues(_.values.sum).toMap
                    val sorted = totalByPlayer.toList.sortBy(-_._2).take(10)
                    val maxV = sorted.map(_._2).maxOption.filter(_ > 0).getOrElse(0.01)
                    val bars = sorted.map { case (pid, v) => s"$pid: ${"=" * math.round(v / maxV * 10).toInt.min(15)} ${f"$v%.2f"}" }.mkString(" | ")
                    div(
                      p(span("VAEP per zawodnik (słupki): "), span(cls := "font-mono text-xs", bars)),
                      buildVaepBarChart(totalByPlayer)
                    )
                  }),
                  s.influenceByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => {
                    val lines = m.toList.take(8).map { case (pid, zones) =>
                      val topZones = zones.toList.sortBy(-_._2).take(3).map { case (z, c) => s"strefa $z: $c" }.mkString(", ")
                      s"$pid: $topZones"
                    }.mkString("; ")
                    p(s"Player Influence (top strefy per gracz): $lines")
                  }),
                  (s.avgDefendersInConeByZone.filter(_.size == 12), s.avgGkDistanceByZone.filter(_.size == 12)) match {
                    case (Some(defs), Some(gk)) => p(s"C-OBSO (kontekst strzałów): obrońcy w stożku strefy 1–12: ${defs.map(d => f"$d%.1f").mkString(", ")}; odl. GK: ${gk.map(g => f"$g%.0f").mkString(", ")} m")
                    case _ => emptyNode
                  },
                  s.setPieceZoneActivity.filter(_.nonEmpty).fold(emptyNode)(m => p(s"Stałe fragmenty (aktywność stref per routine): ${m.keys.mkString(", ")}")),
                  s.pressingInOppHalfByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"Pressing w połowie przeciwnika: ${m.toList.sortBy(-_._2).take(5).map { case (pid, n) => s"$pid: $n" }.mkString(", ")}")),
                  s.playerTortuosityByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"Tortuosity biegów (ścieżka/odcinek): ${m.toList.sortBy(-_._2).take(5).map { case (pid, t) => s"$pid: ${f"$t%.2f"}" }.mkString(", ")}")),
                  s.metabolicLoadByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"Metabolic load per gracz (m): ${m.toList.sortBy(-_._2).take(5).map { case (pid, d) => s"$pid: ${f"$d%.0f"}" }.mkString(", ")}")),
                  s.iwpByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"IWP (pojedynki): ${m.toList.sortBy(-_._2).take(5).map { case (pid, v) => s"$pid: ${f"$v%.2f"}" }.mkString(", ")}")),
                  s.setPiecePatternW.filter(_.nonEmpty).fold(emptyNode)(w => p(s"NMF wzorce (wagi 2 komponentów per routine): ${w.keys.mkString(", ")}")),
                  s.setPieceRoutineCluster.filter(_.nonEmpty).fold(emptyNode)(cl => p(s"Stałe fragmenty – klaster (0/1) per routine: ${cl.map { case (k, v) => s"$k→$v" }.mkString(", ")}")),
                  s.poissonPrognosis.filter(_.size >= 3).fold(emptyNode)(list => p(s"Prognoza Poisson (xG): P(wygrana gosp.) ${f"${list(0) * 100}%.0f%%"}, P(remis) ${f"${list(1) * 100}%.0f%%"}, P(wygrana gości) ${f"${list(2) * 100}%.0f%%"}")),
                  s.voronoiCentroidByZone.filter(_.size == 12).fold(emptyNode)(vor => p(s"Voronoi (centrum aktywności, strefy 1–12): ${vor.map(v => if (v >= 0.5) "G" else "A").mkString}")),
                  s.passValueTotal.filter(_.size >= 2).fold(emptyNode)(pv => p(s"xPass suma (gosp.–goście): ${f"${pv(0)}%.2f"} – ${f"${pv(1)}%.2f"}")),
                  s.passValueByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"xPass (top 5): ${m.toList.sortBy(-_._2).take(5).map { case (pid, v) => s"$pid: ${f"$v%.2f"}" }.mkString(", ")}")),
                  s.passValueUnderPressureTotal.filter(_.size >= 2).fold(emptyNode)(pv => p(s"xPass under pressure (gosp.–goście): ${f"${pv(0)}%.2f"} – ${f"${pv(1)}%.2f"}")),
                  s.passValueUnderPressureByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"xPass under pressure (top 5): ${m.toList.sortBy(-_._2).take(5).map { case (pid, v) => s"$pid: ${f"$v%.2f"}" }.mkString(", ")}")),
                  s.influenceScoreByPlayer.filter(_.nonEmpty).fold(emptyNode)(m => p(s"Influence score (top 5): ${m.toList.sortBy(-_._2).take(5).map { case (pid, sc) => s"$pid: ${f"$sc%.2f"}" }.mkString(", ")}")),
                  buildXgTimeline(m, log),
                  buildPossessionByTime(m, log),
                  buildPressByZone(m, log),
                  div(
                    cls := "mt-2 flex items-center gap-2",
                    label(cls := "text-xs", "Filtr drużyny:"),
                    select(
                      cls := "px-1 py-0.5 border rounded text-xs dark:bg-gray-700",
                      option(value := "all", "Obie"),
                      option(value := "home", "Gospodarze"),
                      option(value := "away", "Goście"),
                      value <-- playerTeamFilter.signal,
                      onChange.mapToValue --> playerTeamFilter.writer
                    )
                  ),
                  buildPerPlayerTable(s, playerTeamFilter.now(), homePlayerIds, awayPlayerIds)
                )
              }
              case _ => emptyNode
            }
            ),
            div(
              styleAttr <-- matchDetailTab.signal.map(t => if (t == "events") "display: block" else "display: none"),
              h2(cls := "text-lg font-semibold mb-2", "Zdarzenia"),
            div(
              cls := "mb-2",
              label(cls := "text-sm mr-2", "Typ: "),
              select(
                cls := "px-2 py-1 border rounded dark:bg-gray-700 text-sm",
                eventTypeFilterOptions.map(opt => option(value := opt, opt)),
                value <-- eventTypeFilter.signal,
                onChange --> { e => eventTypeFilter.set(e.target.asInstanceOf[org.scalajs.dom.html.Select].value) }
              )
            ),
            child <-- logData.signal.combineWith(eventTypeFilter.signal).combineWith(logLoadingMore.signal).combineWith(visibleEventCount.signal).map { x =>
              val logOpt: Option[MatchLogDto] = x._1
              val filterType: String = x._2
              val loadingMore: Boolean = x._3
              val visible = x._4
              logOpt.fold[Element](
                if (m.status == "Played") div(cls := "text-gray-500", "Ładowanie logu...") else div()
              ) { currentLog =>
                val filtered = if (filterType == "Wszystkie") currentLog.events else currentLog.events.filter(_.eventType == filterType)
                val sorted = filtered.sortBy(_.minute)
                val hasMoreFromApi = currentLog.total.exists(_ > currentLog.events.size)
                val showMoreApiButton = hasMoreFromApi && !loadingMore
                val visibleSlice = sorted.take(visible)
                val hasMoreFromList = sorted.size > visible
                val nodes = visibleSlice.map { e =>
                  div(
                    cls := "p-1 rounded bg-gray-50 dark:bg-gray-800",
                    s"${e.minute}' ${e.eventType}" + e.outcome.fold("")(" – " + _)
                  )
                }
                div(
                  if (sorted.isEmpty) div(cls := "text-gray-500 text-sm", "Brak zdarzeń")
                  else div((Seq(cls := "space-y-1 text-sm") ++ nodes): _*),
                  if (hasMoreFromList)
                    button(
                      cls := "mt-2 mr-2 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-600 rounded hover:bg-gray-300",
                      s"Pokaż kolejne 50 (${sorted.size - visible} pozostałych)",
                      onClick --> { _ => visibleEventCount.update(n => (n + 50).min(sorted.size)) }
                    )
                  else emptyNode,
                  if (showMoreApiButton)
                    button(
                      cls := "mt-2 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-600 rounded hover:bg-gray-300",
                      "Załaduj więcej z serwera",
                      onClick --> { _ =>
                        logLoadingMore.set(true)
                        loadLog(logData.now().fold(0)(_.events.size))
                      }
                    )
                  else if (loadingMore) div(cls := "mt-2 text-sm text-gray-500", "Ładowanie...")
                  else emptyNode
                )
              }
            }
            )
          )
        case None => div(cls := "text-gray-500", "Ładowanie...")
      }
    )
  }
}
