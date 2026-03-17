package app

import fmgame.shared.api._
import com.raquo.laminar.api.L.*

object TeamPage {
  private def opt(v: String, label: String) = option(com.raquo.laminar.api.L.value := v, label)
  def render(teamId: String, goBack: () => Unit): Element = {
    val team = Var[Option[TeamDto]](None)
    val players = Var[List[PlayerDto]](Nil)
    val gamePlans = Var[List[GamePlanSnapshotDto]](Nil)
    val error = Var[Option[String]](None)
    val savePlanName = Var("")
    val savePlanFormation = Var("4-3-3")
    val savePlanPressZones = Var[Set[Int]](Set.empty)
    val savePlanCounterZone = Var[Option[Int]](None)
    val savePlanJson = Var("{}")
    val savePlanError = Var[Option[String]](None)
    val savePlanBusy = Var(false)
    val editingPlayerId = Var[Option[String]](None)
    val editFirstName = Var("")
    val editLastName = Var("")
    val editError = Var[Option[String]](None)
    val trainingWeek = Var[List[String]](List.fill(7)("Balanced"))
    val trainingBusy = Var(false)
    val trainingMsg = Var[Option[String]](None)

    def load(): Unit = {
      App.runZio(ApiClient.getTeam(teamId)) {
        case Right(t) => team.set(Some(t))
        case Left(m)  => error.set(Some(m))
      }
      App.runZio(ApiClient.getTeamPlayers(teamId)) {
        case Right(p) => players.set(p); case Left(_) =>
      }
      App.runZio(ApiClient.getGamePlans(teamId)) {
        case Right(g) => gamePlans.set(g); case Left(_) => gamePlans.set(Nil)
      }
      AppState.token.now().foreach { tok =>
        App.runZio(ApiClient.getTrainingPlan(tok, teamId)) {
          case Right(p) => trainingWeek.set(p.week.padTo(7, "Balanced").take(7))
          case Left(_)  =>
        }
      }
    }

    def buildGamePlanJson(): String = {
      val formation = savePlanFormation.now()
      val pressZones = savePlanPressZones.now().toList.sorted
      val counterZone = savePlanCounterZone.now()
      val raw = savePlanJson.now().trim
      if (raw.nonEmpty && raw != "{}") return raw
      val trigger = (pressZones.nonEmpty || counterZone.nonEmpty) match {
        case true => s""","triggerConfig":{"pressZones":[${pressZones.mkString(",")}]${counterZone.fold("")(z => s""","counterTriggerZone":$z""")}}"""
        case false => ""
      }
      s"""{"formationName":"$formation"$trigger}"""
    }

    val trainingOptions: List[(String, String)] = List(
      ("Balanced", "Balanced"),
      ("Attacking", "Attacking"),
      ("Defending", "Defending"),
      ("Physical", "Physical"),
      ("Mental", "Mental"),
      ("Recovery", "Recovery"),
      ("Rest", "Rest")
    )
    val dayLabels = List("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Nd")

    def saveTraining(): Unit = AppState.token.now() match {
      case None => trainingMsg.set(Some("Zaloguj się"))
      case Some(tok) =>
        trainingBusy.set(true)
        trainingMsg.set(None)
        App.runZio(ApiClient.upsertTrainingPlan(tok, teamId, UpsertTrainingPlanRequest(trainingWeek.now()))) {
          case Right(p) =>
            trainingWeek.set(p.week.padTo(7, "Balanced").take(7))
            trainingBusy.set(false)
            trainingMsg.set(Some("Plan treningowy zapisany"))
          case Left(m) =>
            trainingBusy.set(false)
            trainingMsg.set(Some(m))
        }
    }

    def doSavePlan(): Unit = AppState.token.now() match {
      case None => savePlanError.set(Some("Zaloguj się"))
      case Some(tok) =>
        val name = savePlanName.now().trim
        if (name.isEmpty) { savePlanError.set(Some("Podaj nazwę")); return }
        savePlanError.set(None)
        savePlanBusy.set(true)
        val json = buildGamePlanJson()
        App.runZio(ApiClient.saveGamePlan(tok, teamId, SaveGamePlanRequest(name, json))) {
          case Right(_) => savePlanName.set(""); load(); savePlanBusy.set(false)
          case Left(m)  => savePlanError.set(Some(m)); savePlanBusy.set(false)
        }
    }

    def startEdit(p: PlayerDto): Unit = {
      editingPlayerId.set(Some(p.id))
      editFirstName.set(p.firstName)
      editLastName.set(p.lastName)
      editError.set(None)
    }

    def cancelEdit(): Unit = {
      editingPlayerId.set(None)
      editError.set(None)
    }

    def saveEdit(): Unit = AppState.token.now() match {
      case None => editError.set(Some("Zaloguj się"))
      case Some(tok) =>
        val pid = editingPlayerId.now().getOrElse("")
        if (pid.isEmpty) return
        val fn = editFirstName.now().trim
        val ln = editLastName.now().trim
        if (fn.isEmpty && ln.isEmpty) { editError.set(Some("Podaj imię lub nazwisko")); return }
        editError.set(None)
        App.runZio(ApiClient.updatePlayer(tok, pid, UpdatePlayerRequest(Some(fn).filter(_.nonEmpty), Some(ln).filter(_.nonEmpty)))) {
          case Right(_) => cancelEdit(); load()
          case Left(m)  => editError.set(Some(m))
        }
    }

    div(
      cls := "max-w-2xl mx-auto",
      onMountCallback { _ => load() },
      button(
        cls := "mb-4 px-3 py-1 text-sm bg-gray-200 dark:bg-gray-700 rounded hover:bg-gray-300",
        "← Back",
        onClick --> { _ => goBack() }
      ),
      child <-- error.signal.map {
        case Some(m) => div(cls := "text-red-600 mb-4", m)
        case None    => emptyNode
      },
      child <-- team.signal.map {
        case Some(t) =>
          div(
            h1(cls := "text-xl font-bold mb-2", t.name),
            p(cls := "text-sm text-gray-500 mb-4", s"Budget: ${t.budget} · Elo: ${t.eloRating.toInt}" + t.managerName.fold("")(" · Trener: " + _)),
            h2(cls := "text-lg font-semibold mb-2", "Squad"),
            div(
              cls := "space-y-2",
              children <-- players.signal.combineWith(editingPlayerId.signal).map { case (list, editingId) =>
                list.map { p =>
                  val isEditing = editingId.contains(p.id)
                  if (isEditing) {
                    div(
                      cls := "p-2 rounded bg-gray-200 dark:bg-gray-600 flex flex-col gap-2",
                      div(cls := "text-sm font-medium", "Edytuj zawodnika"),
                      input(
                        typ := "text",
                        cls := "px-2 py-1 border rounded dark:bg-gray-700",
                        placeholder := "Imię",
                        controlled(value <-- editFirstName.signal, onInput.mapToValue --> editFirstName.writer)
                      ),
                      input(
                        typ := "text",
                        cls := "px-2 py-1 border rounded dark:bg-gray-700",
                        placeholder := "Nazwisko",
                        controlled(value <-- editLastName.signal, onInput.mapToValue --> editLastName.writer)
                      ),
                      child <-- editError.signal.map {
                        case Some(m) => div(cls := "text-red-600 text-sm", m)
                        case None    => emptyNode
                      },
                      div(
                        cls := "flex gap-2",
                        button(cls := "px-2 py-1 text-sm bg-blue-600 text-white rounded", "Zapisz", onClick --> { _ => saveEdit() }),
                        button(cls := "px-2 py-1 text-sm bg-gray-500 text-white rounded", "Anuluj", onClick --> { _ => cancelEdit() })
                      )
                    )
                  } else {
                    div(
                      cls := "p-2 rounded bg-gray-100 dark:bg-gray-700",
                      div(
                        cls := "flex justify-between items-center",
                        span(cls := "font-medium", s"${p.firstName} ${p.lastName}"),
                        span(
                          cls := "flex items-center gap-2 flex-wrap",
                          span(cls := "text-sm", s"Pozycje: ${p.preferredPositions.mkString(", ")}"),
                          RolePresets.defaultRoleForPositions(p.preferredPositions).fold(emptyNode: Node) { roleId =>
                            span(cls := "text-sm text-gray-500", s"Rola domyślna: ${RolePresets.roleIdToLabel(roleId)}")
                          },
                          span(cls := "text-sm text-gray-500", s"Freshness: ${(p.freshness * 100).toInt}% · Morale: ${(p.morale * 100).toInt}%"),
                          p.injury.fold(emptyNode)(inj => span(cls := "text-amber-600 text-sm", inj)),
                          button(
                            cls := "text-sm text-blue-600 dark:text-blue-400 hover:underline",
                            "Edytuj",
                            onClick --> { _ => startEdit(p) }
                          )
                        )
                      ),
                      if (p.physical.nonEmpty || p.technical.nonEmpty || p.mental.nonEmpty || p.traits.nonEmpty) {
                        div(
                          cls := "mt-2 pt-2 border-t border-gray-200 dark:border-gray-600 text-xs grid grid-cols-3 gap-x-4 gap-y-1",
                          if (p.physical.nonEmpty) div(span(cls := "text-gray-500", "Fizyczne: "), span(p.physical.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))) else emptyNode,
                          if (p.technical.nonEmpty) div(span(cls := "text-gray-500", "Techniczne: "), span(p.technical.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))) else emptyNode,
                          if (p.mental.nonEmpty) div(span(cls := "text-gray-500", "Mentalne: "), span(p.mental.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))) else emptyNode,
                          if (p.traits.nonEmpty) div(span(cls := "text-gray-500", "Cechy: "), span(p.traits.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))) else emptyNode
                        )
                      } else emptyNode
                    )
                  }
                }
              }
            ),
            h2(cls := "text-lg font-semibold mt-6 mb-2", "Zapisane taktyki"),
            div(
              cls := "space-y-2 mb-4",
              children <-- gamePlans.signal.map { list =>
                if (list.isEmpty) Seq(div(cls := "text-gray-500 text-sm", "Brak zapisanych taktyk"))
                else list.map(g => div(cls := "p-2 rounded bg-gray-100 dark:bg-gray-700 text-sm", g.name)).toSeq
              }
            ),
            div(cls := "flex flex-col gap-3",
              input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700 w-64", placeholder := "Nazwa taktyki",
                controlled(value <-- savePlanName.signal, onInput.mapToValue --> savePlanName.writer)),
              div(
                label(cls := "block text-sm font-medium mb-1", "Formacja"),
                select(
                  cls := "px-2 py-1 border rounded dark:bg-gray-700",
                  value <-- savePlanFormation.signal,
                  onChange --> { ev => savePlanFormation.set(ev.target.asInstanceOf[org.scalajs.dom.html.Select].value) },
                  opt("4-3-3", "4-3-3"), opt("4-4-2", "4-4-2"), opt("4-2-3-1", "4-2-3-1"), opt("3-5-2", "3-5-2")
                )
              ),
              div(
                label(cls := "block text-sm font-medium mb-1", "Strefy pressingu (1–12, w których włączony pressing)"),
                div(cls := "flex flex-wrap gap-2",
                  (1 to 12).map { z =>
                    label(
                      cls := "inline-flex items-center gap-1 text-sm",
                      input(
                        typ := "checkbox",
                        checked <-- savePlanPressZones.signal.map(_.contains(z)),
                        onChange --> { _ => savePlanPressZones.update(s => if (s.contains(z)) s - z else s + z) }
                      ),
                      span(s"$z")
                    )
                  }.toSeq
                )
              ),
              div(
                label(cls := "block text-sm font-medium mb-1", "Strefa kontry (odzyskanie piłki w tej strefie wyzwala kontratak)"),
                select(
                  cls := "px-2 py-1 border rounded dark:bg-gray-700",
                  value <-- savePlanCounterZone.signal.map(_.fold("")(_.toString)),
                  onChange --> { ev =>
                    val v = ev.target.asInstanceOf[org.scalajs.dom.html.Select].value
                    savePlanCounterZone.set(if (v.isEmpty) None else scala.util.Try(v.toInt).toOption)
                  },
                  opt("", "— brak —"),
                  opt("1", "Strefa 1"), opt("2", "Strefa 2"), opt("3", "Strefa 3"), opt("4", "Strefa 4"),
                  opt("5", "Strefa 5"), opt("6", "Strefa 6"), opt("7", "Strefa 7"), opt("8", "Strefa 8"),
                  opt("9", "Strefa 9"), opt("10", "Strefa 10"), opt("11", "Strefa 11"), opt("12", "Strefa 12")
                )
              ),
              p(cls := "text-xs text-gray-500", "Zaawansowane: poniżej możesz wkleić pełny JSON (nadpisze ustawienia powyżej)."),
              input(typ := "text", cls := "px-2 py-1 border rounded dark:bg-gray-700 text-sm font-mono w-full", placeholder := "{} (opcjonalnie)",
                controlled(value <-- savePlanJson.signal, onInput.mapToValue --> savePlanJson.writer)),
              child <-- savePlanError.signal.map { case Some(m) => div(cls := "text-red-600 text-sm", m); case None => emptyNode },
              button(cls := "px-4 py-2 bg-blue-600 text-white rounded w-fit", disabled <-- savePlanBusy.signal, "Zapisz taktykę", onClick --> { _ => doSavePlan() })
            )
            ,
            h2(cls := "text-lg font-semibold mt-6 mb-2", "Trening (MVP)"),
            div(
              cls := "p-3 rounded border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-sm space-y-2",
              p(cls := "text-xs text-gray-500", "Plan 7 sesji (Pon–Nd). Wpływa na regenerację (+Rest/Recovery) i minimalny rozwój atrybutów."),
              child <-- trainingMsg.signal.map {
                case Some(m) => div(cls := "text-sm text-blue-600 dark:text-blue-400", m)
                case None    => emptyNode
              },
              div(
                cls := "grid grid-cols-1 md:grid-cols-2 gap-2",
                children <-- trainingWeek.signal.map { w =>
                  dayLabels.zipWithIndex.map { case (d, idx) =>
                    div(
                      label(cls := "block text-xs text-gray-500 mb-1", d),
                      select(
                        cls := "w-full px-2 py-1 border rounded dark:bg-gray-700",
                        value <-- trainingWeek.signal.map(_.lift(idx).getOrElse("Balanced")),
                        onChange --> { ev =>
                          val v = ev.target.asInstanceOf[org.scalajs.dom.html.Select].value
                          trainingWeek.update { cur =>
                            val arr = cur.padTo(7, "Balanced").take(7).toArray
                            arr(idx) = v
                            arr.toList
                          }
                        },
                        trainingOptions.map { case (v, l) => opt(v, l) }
                      )
                    )
                  }.toSeq
                }
              ),
              button(
                cls := "px-4 py-2 bg-green-600 text-white rounded w-fit disabled:opacity-50",
                disabled <-- trainingBusy.signal,
                "Zapisz trening",
                onClick --> { _ => saveTraining() }
              )
            )
          )
        case None => div(cls := "text-gray-500", "Loading...")
      }
    )
  }
}
