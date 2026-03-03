package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*

object MatchSquadPage {
  private def opt(v: String, label: String) = option(com.raquo.laminar.api.L.value := v, label)
  def render(matchId: String, teamId: String, goBack: () => Unit): Element = {
    val players = Var[List[PlayerDto]](Nil)
    val gamePlans = Var[List[GamePlanSnapshotDto]](Nil)
    val selectedGamePlanId = Var[Option[String]](None)
    val selectedGamePlanJson = Var("{}")
    val selectedFormation = Var("4-3-3")
    val selected = Var[Map[Int, String]](Map.empty)
    val customSlotPositions = Var[List[(Double, Double)]](FormationPresets.DefaultPositions433)
    val slotRoles = Var[Map[String, String]](Map.empty)
    val cornerTaker = Var("")
    val freeKickTaker = Var("")
    val penaltyTaker = Var("")
    val throwInTaker = Var("")
    val cornerRoutine = Var("default")
    val freeKickRoutine = Var("default")
    val opponentPlayers = Var[List[PlayerDto]](Nil)
    val oiTarget1 = Var("")
    val oiPress1 = Var("")
    val oiTackle1 = Var("")
    val oiMark1 = Var("")
    val oiTarget2 = Var("")
    val oiPress2 = Var("")
    val oiTackle2 = Var("")
    val oiMark2 = Var("")
    val teamTempo = Var("")
    val teamWidth = Var("")
    val teamPassing = Var("")
    val teamPressingIntensity = Var("")
    val playerInstructions = Var[Map[String, Map[String, String]]](Map.empty)
    val pressZones = Var[Set[Int]](Set.empty)
    val counterZone = Var[Option[Int]](None)
    val selectedGameStyle = Var("")
    val showPlayerList = Var(false)
    val expandedAttributes = Var[Set[Int]](Set.empty)
    val error = Var[Option[String]](None)
    val success = Var[Option[String]](None)
    val busy = Var(false)
    val assistantTip = Var[Option[String]](None)

    def load(): Unit = {
      App.runZio(ApiClient.getTeamPlayers(teamId)) {
        case Right(p) => players.set(p)
        case Left(m)  => error.set(Some(m))
      }
      // wczytaj rywala (dla instrukcji na rywala)
      App.runZio(ApiClient.getMatch(matchId)) {
        case Right(m) =>
          val oppId = if (m.homeTeamId == teamId) m.awayTeamId else m.homeTeamId
          App.runZio(ApiClient.getTeamPlayers(oppId)) {
            case Right(p) => opponentPlayers.set(p)
            case Left(_)  => opponentPlayers.set(Nil)
          }
        case Left(_) => opponentPlayers.set(Nil)
      }
      App.runZio(ApiClient.getGamePlans(teamId)) {
        case Right(g) => gamePlans.set(g)
        case Left(_)  => gamePlans.set(Nil)
      }
      // rada asystenta (wymaga tokenu)
      AppState.token.now().foreach { _ =>
        App.runZio(ApiClient.getAssistantTip(matchId, teamId)) {
          case Right(dto) => assistantTip.set(Some(dto.tip))
          case Left(_)    => assistantTip.set(None)
        }
      }
    }
    load()

    def onGamePlanSelected(snapshotId: String): Unit =
      if (snapshotId.isEmpty) {
        selectedGamePlanId.set(None)
        selectedGamePlanJson.set("{}")
      } else {
        selectedGamePlanId.set(Some(snapshotId))
        App.runZio(ApiClient.getGamePlanSnapshot(teamId, snapshotId)) {
          case Right(d) => selectedGamePlanJson.set(d.gamePlanJson)
          case Left(_)  => selectedGamePlanJson.set("{}")
        }
      }

    def buildGamePlanJson(): String = {
      val base = selectedGamePlanJson.now().trim
      val formation = selectedFormation.now()
      if (base.nonEmpty && base != "{}" && selectedGamePlanId.now().isDefined) base
      else {
        def esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val pz = pressZones.now().toList.sorted
        val cz = counterZone.now()
        val trigger = if (pz.nonEmpty || cz.nonEmpty)
          s""","triggerConfig":{"pressZones":[${pz.mkString(",")}]${cz.fold("")(z => s""","counterTriggerZone":$z""")}}"""
        else ""
        val customPos = (formation == "Własna") match {
          case true =>
            val pos = customSlotPositions.now().padTo(11, (0.5, 0.5)).take(11)
            s""","customPositions":[${pos.map { case (x, y) => s"[$x,$y]" }.mkString(",")}]"""
          case false => ""
        }
        val roles = slotRoles.now()
        val rolesJson = if (roles.isEmpty) "" else s""","slotRoles":{${roles.map { case (k, v) => s""""${esc(k)}":"${esc(v)}"""" }.mkString(",")}}"""
        val spParts = Seq(
          if (cornerTaker.now().nonEmpty) Some(s""""cornerTakerPlayerId":"${esc(cornerTaker.now())}"""") else None,
          if (freeKickTaker.now().nonEmpty) Some(s""""freeKickTakerPlayerId":"${esc(freeKickTaker.now())}"""") else None,
          if (penaltyTaker.now().nonEmpty) Some(s""""penaltyTakerPlayerId":"${esc(penaltyTaker.now())}"""") else None,
          if (cornerRoutine.now().nonEmpty && cornerRoutine.now() != "default") Some(s""""cornerRoutine":"${esc(cornerRoutine.now())}"""") else None,
          if (freeKickRoutine.now().nonEmpty && freeKickRoutine.now() != "default") Some(s""""freeKickRoutine":"${esc(freeKickRoutine.now())}"""") else None
        ).flatten
        val setPiecesJson = if (spParts.isEmpty) "" else s""","setPieces":{${spParts.mkString(",")}}"""
        val throwInJson = if (throwInTaker.now().trim.nonEmpty) s""","throwInConfig":{"defaultTakerPlayerId":"${esc(throwInTaker.now().trim)}"}""" else ""
        def oiObj(target: String, press: String, tackle: String, mark: String): Option[String] =
          if (target.trim.isEmpty) None
          else {
            val parts = Seq(
              Some(s""""targetPlayerId":"${esc(target.trim)}""""),
              Option(press.trim).filter(_.nonEmpty).map(v => s""""pressIntensity":"${esc(v)}""""),
              Option(tackle.trim).filter(_.nonEmpty).map(v => s""""tackle":"${esc(v)}""""),
              Option(mark.trim).filter(_.nonEmpty).map(v => s""""mark":"${esc(v)}"""")
            ).flatten
            Some(s"""{${parts.mkString(",")}}""")
          }
        val oiList = List(
          oiObj(oiTarget1.now(), oiPress1.now(), oiTackle1.now(), oiMark1.now()),
          oiObj(oiTarget2.now(), oiPress2.now(), oiTackle2.now(), oiMark2.now())
        ).flatten
        val oiJson = if (oiList.isEmpty) "" else s""","oppositionInstructions":[${oiList.mkString(",")}]"""
        val ti = (teamTempo.now(), teamWidth.now(), teamPassing.now(), teamPressingIntensity.now())
        val teamInstrJson = (ti._1.nonEmpty || ti._2.nonEmpty || ti._3.nonEmpty || ti._4.nonEmpty) match {
          case true =>
            val parts = Seq(
              if (ti._1.nonEmpty) s""""tempo":"${esc(ti._1)}"""" else "",
              if (ti._2.nonEmpty) s""""width":"${esc(ti._2)}"""" else "",
              if (ti._3.nonEmpty) s""""passingDirectness":"${esc(ti._3)}"""" else "",
              if (ti._4.nonEmpty) s""""pressingIntensity":"${esc(ti._4)}""""
            ).filter(_.nonEmpty)
            s""","teamInstructions":{${parts.mkString(",")}}"""
          case false => ""
        }
        val pi = playerInstructions.now().filter { case (_, m) => m.nonEmpty }
        val playerInstrJson = if (pi.isEmpty) "" else s""","playerInstructions":{${pi.map { case (slot, m) => s""""${esc(slot)}":{${m.map { case (k, v) => s""""$k":"${esc(v)}"""" }.mkString(",")}}""" }.mkString(",")}}"""
        val formationName = if (formation == "Własna") "4-3-3" else formation
        if (formationName.nonEmpty) s"""{"formationName":"$formationName"$trigger$customPos$rolesJson$teamInstrJson$playerInstrJson$setPiecesJson$throwInJson$oiJson}""" else "{}"
      }
    }

    def submit(): Unit = AppState.token.now() match {
      case None => error.set(Some("Zaloguj się"))
      case Some(tok) =>
        val sel = selected.now()
        if (sel.size != 11) { error.set(Some("Wybierz dokładnie 11 zawodników")); return }
        val slots = FormationPresets.slots(selectedFormation.now())
        val lineup = slots.zipWithIndex.map { case (pos, i) => LineupSlotDto(sel(i), pos) }
        val gamePlanJson = buildGamePlanJson()
        error.set(None)
        success.set(None)
        busy.set(true)
        App.runZio(ApiClient.submitMatchSquad(tok, matchId, teamId, SubmitMatchSquadRequest(lineup, gamePlanJson))) {
          case Right(_) =>
            success.set(Some("Skład zapisany"))
            AppState.lineupContext.set(None)
            busy.set(false)
          case Left(m) =>
            error.set(Some(m))
            busy.set(false)
        }
    }

    div(
      cls := "max-w-xl mx-auto",
      button(
        cls := "mb-4 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-700 rounded hover:bg-gray-300",
        "← Wstecz",
        onClick --> { _ => goBack() }
      ),
      h1(cls := "text-xl font-bold mb-4", "Ustaw skład (11 zawodników)"),
      child <-- assistantTip.signal.map {
        case Some(tip) =>
          div(cls := "mb-4 p-3 rounded bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-700 text-sm", title := "Rada asystenta przed meczem",
            span(cls := "font-medium text-amber-800 dark:text-amber-200", "Rada asystenta: "),
            span(tip)
          )
        case None => emptyNode
      },
      child <-- error.signal.map {
        case Some(m) => div(cls := "text-red-600 text-sm mb-2", m)
        case None    => emptyNode
      },
      child <-- success.signal.map {
        case Some(m) => div(cls := "text-green-600 text-sm mb-2", m)
        case None    => emptyNode
      },
      div(
        cls := "mb-4 space-y-2",
        div(
          label(cls := "block text-sm font-medium mb-1", span("Formacja"), span(cls := "ml-1 text-gray-500 cursor-help", title := "Ustawienie 11 zawodników na boisku (np. 4-3-3, 4-4-2). Wpływa na pozycjonowanie w symulacji.", "ⓘ")),
          select(
            cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
            value <-- selectedFormation.signal,
            onChange --> { ev =>
              selectedFormation.set(ev.target.asInstanceOf[org.scalajs.dom.html.Select].value)
            },
            opt("4-3-3", "4-3-3"), opt("4-4-2", "4-4-2"), opt("4-2-3-1", "4-2-3-1"), opt("3-5-2", "3-5-2"), opt("Własna", "Własna (przeciągnij pozycje)")
          )
        ),
        child <-- selectedFormation.signal.combineWith(players.signal).combineWith(selected.signal).map { case (formation, ps, selMap) =>
          val slots = FormationPresets.slots(formation)
          val basePositions =
            if (formation == "Własna") customSlotPositions
            else {
              // dla gotowych formacji użyj domyślnych pozycji 4-3-3 jako przybliżenia
              customSlotPositions.set(FormationPresets.DefaultPositions433)
              customSlotPositions
            }
          val playerNames = Var(ps.map(p => p.id -> s"${p.firstName} ${p.lastName}").toMap)
          div(
            label(cls := "block text-sm font-medium mb-1", if (formation == "Własna") "Boisko (przeciągaj kółka, aby zmienić ustawienie)" else "Boisko (podgląd ustawienia)"),
            PitchView.render(basePositions, slots, selected.signal, playerNames.signal, formation == "Własna")
          )
        }
      ),
      div(
        cls := "mb-4",
        label(cls := "block text-sm font-medium mb-1", span("Styl gry"), span(cls := "ml-1 text-gray-500 cursor-help", title := "Szablon taktyki: ustawia tempo, pressing i strefy. Możesz potem ręcznie poprawić.", "ⓘ")),
        select(
          cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
          value <-- selectedGameStyle.signal,
          onChange --> { ev =>
            val v = ev.target.asInstanceOf[org.scalajs.dom.html.Select].value
            selectedGameStyle.set(v)
            GameStylePresets.byId(v).foreach { p =>
              teamTempo.set(p.tempo)
              teamWidth.set(p.width)
              teamPassing.set(p.passing)
              teamPressingIntensity.set(p.pressing)
              pressZones.set(p.pressZones.toSet)
              counterZone.set(p.counterZone)
            }
          },
          opt("", "— brak (ręcznie) —"),
          opt(GameStylePresets.Gegenpress.id, GameStylePresets.Gegenpress.label),
          opt(GameStylePresets.LowBlock.id, GameStylePresets.LowBlock.label),
          opt(GameStylePresets.TikiTaka.id, GameStylePresets.TikiTaka.label)
        ),
        child <-- selectedGameStyle.signal.map { id =>
          GameStylePresets.byId(id).fold(emptyNode)(p => div(cls := "text-xs text-gray-500 mt-1", p.description))
        }
      ),
      div(
        cls := "mb-4 p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800",
        h3(cls := "text-sm font-semibold mb-2", "Taktyka i strategia"),
        div(
          cls := "mb-2",
          label(cls := "block text-sm font-medium mb-1", span("Strefy pressingu (1–12)"), span(cls := "ml-1 text-gray-500 cursor-help", title := "W których strefach boiska (1–12) drużyna ma aktywnie odbierać piłkę. Wpływa na bonus przechwytu w symulacji.", "ⓘ")),
          div(
            cls := "flex flex-wrap gap-2",
            (1 to 12).toList.map { z =>
              label(
                cls := "inline-flex items-center gap-1 text-sm",
                input(
                  tpe := "checkbox",
                  checked <-- pressZones.signal.map(_.contains(z)),
                  onChange --> { _ => pressZones.update(s => if (s.contains(z)) s - z else s + z) }
                ),
                span(s"$z")
              )
            }
          )
        ),
        div(
          cls := "mb-2",
          label(cls := "block text-sm font-medium mb-1", span("Strefa kontry (1–12, opcjonalnie)"), span(cls := "ml-1 text-gray-500 cursor-help", title := "Po odzyskaniu piłki w tej strefie silnik zwiększa szansę na szybki strzał (kontra).", "ⓘ")),
          input(
            tpe := "number",
            placeholder := "1–12 lub puste",
            cls := "w-20 px-2 py-1 border rounded dark:bg-gray-700",
            value <-- counterZone.signal.map(_.fold("")(_.toString)),
            onInput --> { ev =>
              val v = ev.target.asInstanceOf[org.scalajs.dom.html.Input].value.trim
              counterZone.set(if (v.isEmpty) None else Some(v.toIntOption.getOrElse(1).max(1).min(12)))
            }
          )
        ),
        h4(cls := "text-sm font-medium mt-3 mb-1", "Instrukcje drużynowe (FM-style)"),
        p(cls := "text-xs text-gray-500 mb-1", "Wartości: tempo=lower|normal|higher, szerokość=narrow|normal|wide, podania=shorter|normal|direct, pressing=lower|normal|higher"),
        div(
          cls := "grid grid-cols-2 gap-2 text-sm",
          div(
            label(cls := "block mb-0.5", span("Tempo"), span(cls := "ml-1 text-gray-500 cursor-help", title := "Szybkość gry: lower = wolniejsze budowanie, higher = więcej akcji w czasie.", "ⓘ")),
            input(
              typ := "text",
              cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
              placeholder := "lower, normal, higher",
              value <-- teamTempo.signal,
              onInput.mapToValue --> teamTempo.writer
            )
          ),
          div(
            label(cls := "block mb-0.5", span("Szerokość"), span(cls := "ml-1 text-gray-500 cursor-help", title := "Szerokość ustawienia: narrow = wąsko, wide = rozciągnięcie boiska.", "ⓘ")),
            input(
              typ := "text",
              cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
              placeholder := "narrow, normal, wide",
              value <-- teamWidth.signal,
              onInput.mapToValue --> teamWidth.writer
            )
          ),
          div(
            label(cls := "block mb-0.5", span("Podejście do podań"), span(cls := "ml-1 text-gray-500 cursor-help", title := "shorter = krótsze podania, direct = więcej podań w przód; wpływa na celność w silniku.", "ⓘ")),
            input(
              typ := "text",
              cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
              placeholder := "shorter, normal, direct",
              value <-- teamPassing.signal,
              onInput.mapToValue --> teamPassing.writer
            )
          ),
          div(
            label(cls := "block mb-0.5", span("Intensywność pressingu"), span(cls := "ml-1 text-gray-500 cursor-help", title := "Niższa = mniej agresywny pressing, higher = więcej akcji odbioru i zmęczenia.", "ⓘ")),
            input(
              typ := "text",
              cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
              placeholder := "lower, normal, higher",
              value <-- teamPressingIntensity.signal,
              onInput.mapToValue --> teamPressingIntensity.writer
            )
          )
        )
      ),
        div(
          cls := "mb-4 p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800",
          h3(cls := "text-sm font-semibold mb-2", "Stałe fragmenty (MVP)"),
          p(cls := "text-xs text-gray-500 mb-2", "Wybierz wykonawców spośród swoich zawodników. Rutyny na razie są prostymi presetami."),
          child <-- players.signal.map { ps =>
            val opts = (option(com.raquo.laminar.api.L.value := "", "— brak —") +: ps.map(p => option(com.raquo.laminar.api.L.value := p.id, s"${p.firstName} ${p.lastName}"))).toSeq
            div(
              cls := "grid grid-cols-1 md:grid-cols-2 gap-2 text-sm",
              div(
                label(cls := "block mb-0.5", "Wykonawca rożnych"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  opts,
                  value <-- cornerTaker.signal,
                  onChange.mapToValue --> cornerTaker.writer
                )
              ),
              div(
                label(cls := "block mb-0.5", "Rutyna rożnych"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  option(com.raquo.laminar.api.L.value := "default", "Domyślna"),
                  option(com.raquo.laminar.api.L.value := "near_post", "Na krótki słupek"),
                  option(com.raquo.laminar.api.L.value := "far_post", "Na dalszy słupek"),
                  option(com.raquo.laminar.api.L.value := "short", "Krótko"),
                  value <-- cornerRoutine.signal,
                  onChange.mapToValue --> cornerRoutine.writer
                )
              ),
              div(
                label(cls := "block mb-0.5", "Wykonawca wolnych"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  opts,
                  value <-- freeKickTaker.signal,
                  onChange.mapToValue --> freeKickTaker.writer
                )
              ),
              div(
                label(cls := "block mb-0.5", "Rutyna wolnych"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  option(com.raquo.laminar.api.L.value := "default", "Domyślna"),
                  option(com.raquo.laminar.api.L.value := "direct", "Bezpośredni strzał"),
                  option(com.raquo.laminar.api.L.value := "cross", "Dośrodkowanie"),
                  value <-- freeKickRoutine.signal,
                  onChange.mapToValue --> freeKickRoutine.writer
                )
              ),
              div(
                label(cls := "block mb-0.5", "Wykonawca karnych"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  opts,
                  value <-- penaltyTaker.signal,
                  onChange.mapToValue --> penaltyTaker.writer
                )
              ),
              div(
                label(cls := "block mb-0.5", "Wykonawca wrzutów z autu"),
                select(
                  cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                  opts,
                  value <-- throwInTaker.signal,
                  onChange.mapToValue --> throwInTaker.writer
                )
              )
            )
          },
          p(cls := "text-xs text-gray-500 mt-1", "Na razie wpływa na to, kto jest aktorem zdarzeń Corner/FreeKick/Karny/Wrzut z autu + zwiększa szansę na strzał po stałych fragmentach.")
        ),
      div(
        cls := "mb-4 p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800",
        h3(cls := "text-sm font-semibold mb-2", "Instrukcje na rywala (MVP)"),
        p(cls := "text-xs text-gray-500 mb-2", "Wybierz zawodnika rywala i ustaw: pressIntensity=more_urgent, tackle=harder, mark=tighter."),
        child <-- opponentPlayers.signal.map { opp =>
          if (opp.isEmpty) div(cls := "text-gray-500 text-sm", "Brak danych rywala")
          else {
            def playerOpt(p: PlayerDto) = option(com.raquo.laminar.api.L.value := p.id, s"${p.firstName} ${p.lastName}")
            div(
              cls := "space-y-3",
              div(
                cls := "grid grid-cols-1 md:grid-cols-4 gap-2 text-sm",
                select(cls := "px-2 py-1 border rounded dark:bg-gray-700",
                  option(com.raquo.laminar.api.L.value := "", "— cel 1 —"),
                  opp.map(playerOpt).toSeq,
                  value <-- oiTarget1.signal,
                  onChange.mapToValue --> oiTarget1.writer
                ),
                input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "pressIntensity",
                  controlled(value <-- oiPress1.signal, onInput.mapToValue --> oiPress1.writer)),
                input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "tackle",
                  controlled(value <-- oiTackle1.signal, onInput.mapToValue --> oiTackle1.writer)),
                input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "mark",
                  controlled(value <-- oiMark1.signal, onInput.mapToValue --> oiMark1.writer))
              ),
              div(
                cls := "grid grid-cols-1 md:grid-cols-4 gap-2 text-sm",
                select(cls := "px-2 py-1 border rounded dark:bg-gray-700",
                  option(com.raquo.laminar.api.L.value := "", "— cel 2 —"),
                  opp.map(playerOpt).toSeq,
                  value <-- oiTarget2.signal,
                  onChange.mapToValue --> oiTarget2.writer
                ),
                input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "pressIntensity",
                  controlled(value <-- oiPress2.signal, onInput.mapToValue --> oiPress2.writer)),
                input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "tackle",
                  controlled(value <-- oiTackle2.signal, onInput.mapToValue --> oiTackle2.writer)),
                input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700", placeholder := "mark",
                  controlled(value <-- oiMark2.signal, onInput.mapToValue --> oiMark2.writer))
              )
            )
          }
        }
      ),
      div(
        cls := "mb-4",
        button(
          cls := "text-sm text-blue-600 dark:text-blue-400 hover:underline",
          "Lista zawodników",
          onClick --> { _ => showPlayerList.update(!_) }
        ),
        child <-- showPlayerList.signal.map { show =>
          if (!show) emptyNode
          else
            div(
              cls := "mt-2 p-2 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 max-h-48 overflow-y-auto",
              children <-- players.signal.map { list =>
                list.map { p =>
                  div(
                    cls := "flex justify-between items-center py-1 text-sm border-b border-gray-200 dark:border-gray-600 last:border-0",
                    span(s"${p.firstName} ${p.lastName}"),
                    span(cls := "text-gray-500 dark:text-gray-400", p.preferredPositions.mkString(", "))
                  )
                }.toSeq
              }
            )
        }
      ),
      div(
        cls := "space-y-2 mb-4",
        children <-- selectedFormation.signal.combineWith(players.signal).map { case (formation, playerList) =>
          val slots = FormationPresets.slots(formation)
          slots.zipWithIndex.toList.map { case (pos, idx) =>
            div(
              cls := "p-2 rounded border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800",
              div(
                cls := "flex flex-wrap items-center gap-2",
                label(cls := "w-12 text-sm shrink-0 font-medium", s"$pos:"),
                select(
                  cls := "flex-1 min-w-0 max-w-[200px] px-2 py-1 border rounded dark:bg-gray-700 text-sm",
                  children <-- players.signal.map { list =>
                    (option(com.raquo.laminar.api.L.value := "", "— wybierz —") +: list.map(p => option(com.raquo.laminar.api.L.value := p.id, s"${p.firstName} ${p.lastName} (${p.preferredPositions.mkString(", ")})"))).toSeq
                  },
                  value <-- selected.signal.map(s => s.get(idx).getOrElse("")),
                  onChange --> { ev =>
                    val v = ev.target.asInstanceOf[org.scalajs.dom.html.Select].value
                    selected.update(m => if (v.isEmpty) m - idx else m + (idx -> v))
                  }
                ),
                span(cls := "text-xs text-gray-500 shrink-0", "Rola:"),
                span(
                  cls := "inline-flex items-center gap-0.5",
                  input(
                    typ := "text",
                    cls := "w-28 px-2 py-1 border rounded dark:bg-gray-700 text-sm",
                    placeholder := RolePresets.defaultRoleForSlot(pos).map(r => s"np. $r").getOrElse("np. anchor, ball_winner"),
                    value <-- slotRoles.signal.map(_.get(pos).getOrElse("")),
                    onInput --> { ev =>
                      val v = ev.target.asInstanceOf[org.scalajs.dom.html.Input].value.trim
                      slotRoles.update(m => if (v.isEmpty) m - pos else m + (pos -> v))
                    }
                  ),
                  span(
                    cls := "text-gray-500 cursor-help ml-0.5",
                    title <-- slotRoles.signal.map(m => RolePresets.roleDescription(m.get(pos).getOrElse("")).trim).map(t => if (t.isEmpty) "Wybierz rolę (np. anchor, mezzala) – najważniejsze atrybuty dla roli zobaczysz po najechaniu." else t),
                    "ⓘ"
                  )
                ),
                span(cls := "text-xs text-gray-500 shrink-0", "Pressing:"),
                input(
                  typ := "text",
                  cls := "w-28 px-1 py-0.5 border rounded dark:bg-gray-700 text-xs",
                  placeholder := "more_urgent, less_urgent",
                  value <-- playerInstructions.signal.map(_.get(pos).flatMap(_.get("pressIntensity")).getOrElse("")),
                  onInput --> { ev =>
                    val v = ev.target.asInstanceOf[org.scalajs.dom.html.Input].value.trim
                    playerInstructions.update(m => {
                      val slotMap = m.getOrElse(pos, Map.empty)
                      val newSlot = if (v.isEmpty) slotMap - "pressIntensity" else slotMap + ("pressIntensity" -> v)
                      if (newSlot.isEmpty) m - pos else m + (pos -> newSlot)
                    })
                  }
                ),
                span(cls := "text-xs text-gray-500 shrink-0", "Wślizg:"),
                input(
                  typ := "text",
                  cls := "w-24 px-1 py-0.5 border rounded dark:bg-gray-700 text-xs",
                  placeholder := "harder, ease_off",
                  value <-- playerInstructions.signal.map(_.get(pos).flatMap(_.get("tackle")).getOrElse("")),
                  onInput --> { ev =>
                    val v = ev.target.asInstanceOf[org.scalajs.dom.html.Input].value.trim
                    playerInstructions.update(m => {
                      val slotMap = m.getOrElse(pos, Map.empty)
                      val newSlot = if (v.isEmpty) slotMap - "tackle" else slotMap + ("tackle" -> v)
                      if (newSlot.isEmpty) m - pos else m + (pos -> newSlot)
                    })
                  }
                ),
                span(cls := "text-xs text-gray-500 shrink-0", "Pilnuj:"),
                input(
                  typ := "text",
                  cls := "w-24 px-1 py-0.5 border rounded dark:bg-gray-700 text-xs",
                  placeholder := "tighter, specific_player",
                  value <-- playerInstructions.signal.map(_.get(pos).flatMap(_.get("mark")).getOrElse("")),
                  onInput --> { ev =>
                    val v = ev.target.asInstanceOf[org.scalajs.dom.html.Input].value.trim
                    playerInstructions.update(m => {
                      val slotMap = m.getOrElse(pos, Map.empty)
                      val newSlot = if (v.isEmpty) slotMap - "mark" else slotMap + ("mark" -> v)
                      if (newSlot.isEmpty) m - pos else m + (pos -> newSlot)
                    })
                  }
                )
              ),
              child <-- selected.signal.combineWith(expandedAttributes.signal).combineWith(players.signal).map { case (sel, expanded, list) =>
                val pid = sel.get(idx)
                val showAttrs = expanded.contains(idx)
                if (pid.isEmpty || list.isEmpty) emptyNode
                else {
                  val p = list.find(_.id == pid.get)
                  if (p.isEmpty) emptyNode
                  else {
                    val pl = p.get
                    div(
                      cls := "mt-2 pt-2 border-t border-gray-200 dark:border-gray-600",
                      button(
                        cls := "text-xs text-blue-600 dark:text-blue-400 hover:underline",
                        if (showAttrs) "Ukryj atrybuty" else "Pokaż atrybuty",
                        onClick --> { _ => expandedAttributes.update(s => if (s.contains(idx)) s - idx else s + idx) }
                      ),
                      if (showAttrs) {
                        div(
                          cls := "mt-1 text-xs text-gray-600 dark:text-gray-400 grid grid-cols-3 gap-x-2",
                          if (pl.physical.nonEmpty) div(span(cls := "font-medium", "Fizyczne: "), span(pl.physical.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))) else emptyNode,
                          if (pl.technical.nonEmpty) div(span(cls := "font-medium", "Techniczne: "), span(pl.technical.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))) else emptyNode,
                          if (pl.mental.nonEmpty) div(span(cls := "font-medium", "Mentalne: "), span(pl.mental.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))) else emptyNode,
                          if (pl.traits.nonEmpty) div(span(cls := "font-medium", "Cechy: "), span(pl.traits.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))) else emptyNode
                        )
                      } else emptyNode
                    )
                  }
                }
              }
            )
          }
        }
      ),
      div(
        cls := "mb-4",
        label(cls := "block text-sm font-medium mb-1", "Zapisana taktyka (formacja + pressing; opcjonalnie)"),
        select(
          cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
          option(value := "", "— brak / domyślna —"),
          children <-- gamePlans.signal.map { list =>
            list.map(g => option(value := g.id, g.name)).toSeq
          },
          value <-- selectedGamePlanId.signal.map(_.getOrElse("")),
          onChange --> { e => onGamePlanSelected(e.target.asInstanceOf[org.scalajs.dom.html.Select].value) }
        )
      ),
      p(cls := "text-sm text-gray-500 mb-2", child <-- selected.signal.map(s => s"Wybranych: ${s.size}/11")),
      button(
        cls := "w-full py-2 px-4 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50",
        disabled <-- busy.signal.combineWith(selected.signal).map { case (b, s) => b || s.size != 11 },
        "Zapisz skład",
        onClick --> { _ => submit() }
      )
    )
  }
}
