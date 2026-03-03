package app

/** Presety formacji: nazwa → lista 11 slotów (kolejność zgodna z silnikiem). */
object FormationPresets {
  /** 4-3-3: GK, obrona 4, pomoc 3, atak 3. */
  val Formation433: (String, List[String]) = ("4-3-3", List("GK", "LB", "CB", "RB", "LCM", "CDM", "RCM", "LW", "RW", "ST", "ST"))
  /** 4-4-2: GK, obrona 4, pomoc 4, dwóch napastników. */
  val Formation442: (String, List[String]) = ("4-4-2", List("GK", "LB", "CB", "CB", "RB", "LM", "LCM", "RCM", "RM", "ST", "ST"))
  /** 4-2-3-1: GK, obrona 4, dwóch defensywnych, trójka ofensywnych, jeden ST. */
  val Formation4231: (String, List[String]) = ("4-2-3-1", List("GK", "LB", "CB", "CB", "RB", "LDM", "RDM", "LW", "CAM", "RW", "ST"))
  /** 3-5-2: GK, trzech stoperów, pięć w pomocy, dwóch napastników. */
  val Formation352: (String, List[String]) = ("3-5-2", List("GK", "LCB", "CB", "RCB", "LWB", "LCM", "CM", "RCM", "RWB", "LST", "RST"))
  /** Własna: użytkownik przeciąga pozycje na boisku. Domyślne sloty jak 4-3-3. */
  val FormationCustom: (String, List[String]) = ("Własna", Formation433._2)

  val All: List[(String, List[String])] = List(Formation433, Formation442, Formation4231, Formation352, FormationCustom)
  val ByName: Map[String, List[String]] = All.toMap
  def slots(formationName: String): List[String] = ByName.getOrElse(formationName, Formation433._2)

  /** Pozycje domyślne 4-3-3 (x, y w 0–1): zgodne z backendem PositionGenerator.formation433. */
  val DefaultPositions433: List[(Double, Double)] = List(
    (0.04, 0.5), (0.18, 0.22), (0.18, 0.5), (0.18, 0.78),
    (0.35, 0.35), (0.35, 0.5), (0.35, 0.65),
    (0.55, 0.22), (0.55, 0.78), (0.55, 0.5), (0.55, 0.5)
  )
}

/** Instrukcje drużynowe (FM-style). */
object TeamInstructionPresets {
  val Tempo: List[(String, String)] = List(("", "— domyślne —"), ("lower", "Wolniejsze"), ("normal", "Normalne"), ("higher", "Szybsze"))
  val Width: List[(String, String)] = List(("", "— domyślne —"), ("narrow", "Wąsko"), ("normal", "Normalnie"), ("wide", "Szeroko"))
  val Passing: List[(String, String)] = List(("", "— domyślne —"), ("shorter", "Krótsze podania"), ("normal", "Normalnie"), ("direct", "Długie w przód"))
  val PressingIntensity: List[(String, String)] = List(("", "— domyślne —"), ("lower", "Niższa"), ("normal", "Normalna"), ("higher", "Wyższa"))
}

/** Instrukcje per zawodnik/pozycja (FM-style). */
object PlayerInstructionPresets {
  val PressIntensity: List[(String, String)] = List(("", "— domyślne —"), ("more_urgent", "Bardziej urgentny pressing"), ("less_urgent", "Mniej urgentny pressing"))
  val Tackle: List[(String, String)] = List(("", "— domyślne —"), ("harder", "Twarde wślizgi"), ("ease_off", "Ostrożne wślizgi"))
  val Mark: List[(String, String)] = List(("", "— domyślne —"), ("tighter", "Pilnuj ściślej"), ("specific_player", "Pilnuj konkretnego"))
}

/** Predefiniowane style gry – szablony taktyki (tempo, pressing, strefy). */
object GameStylePresets {
  case class StylePreset(
    id: String,
    label: String,
    description: String,
    tempo: String,
    width: String,
    passing: String,
    pressing: String,
    pressZones: List[Int],
    counterZone: Option[Int]
  )
  val Gegenpress: StylePreset = StylePreset(
    "gegenpress", "Gegenpress",
    "Wysoki pressing, odzyskiwanie piłki wysoko. Wymaga dobrej kondycji (stamina, work rate).",
    "higher", "normal", "direct", "higher",
    (5 to 12).toList, Some(8)
  )
  val LowBlock: StylePreset = StylePreset(
    "low_block", "Low block",
    "Głęboka obrona, rzadki pressing. Oszczędza energię, kontratak ze strefy własnej.",
    "lower", "narrow", "direct", "lower",
    (1 to 4).toList, Some(3)
  )
  val TikiTaka: StylePreset = StylePreset(
    "tiki_taka", "Tiki-taka",
    "Krótkie podania, posiadanie piłki, niski pressing. Wymaga passing, vision, composure.",
    "lower", "wide", "shorter", "lower",
    (6 to 10).toList, None
  )
  val All: List[StylePreset] = List(Gegenpress, LowBlock, TikiTaka)
  def byId(id: String): Option[StylePreset] = All.find(_.id == id)
}

/** Role na pozycji (FM-style) z opisami i kluczowymi atrybutami. */
object RolePresets {
  /** (id, label, opis po polsku, kluczowe atrybuty) */
  val RoleInfo: List[(String, String, String, List[String])] = List(
    ("", "— brak —", "", Nil),
    ("sweeper_keeper", "Sweeper keeper", "Bramkarz wychodzący z linii. Kluczowe: reflexes, kicking, firstTouch, composure.", List("gkReflexes", "gkKicking", "firstTouch", "composure")),
    ("goalkeeper", "Bramkarz", "Klasyczny bramkarz. Kluczowe: reflexes, handling, positioning.", List("gkReflexes", "gkHandling", "gkPositioning")),
    ("full_back", "Full back", "Boczny obrońca. Kluczowe: tackling, stamina, positioning.", List("tackling", "stamina", "positioning")),
    ("wing_back", "Wing back", "Boczny z obowiązkami w ataku. Kluczowe: stamina, crossing, pace.", List("stamina", "crossing", "pace")),
    ("ball_playing_defender", "Ball-playing defender", "Stoper budujący grę od tyłu. Kluczowe: passing, composure, decisions, positioning.", List("passing", "composure", "decisions", "positioning")),
    ("inverted_wing_back", "Inverted wing-back", "Boczny wchodzący do środka z piłką. Kluczowe: dribbling, passing, decisions.", List("dribbling", "passing", "decisions")),
    ("cb_cover", "Stoper (cover)", "Stoper z przewagą w grze na przewagę. Kluczowe: pace, positioning, anticipation.", List("pace", "positioning", "anticipation")),
    ("cb_stopper", "Stoper (stopper)", "Stoper agresywny w wyjściu. Kluczowe: tackling, strength, aggression.", List("tackling", "strength", "aggression")),
    ("anchor", "Anchor", "Defensywny pomocnik przed linią. Kluczowe: tackling, positioning, strength.", List("tackling", "positioning", "strength")),
    ("ball_winner", "Ball winner", "Pomocnik odbierający piłkę. Kluczowe: tackling, aggression, workRate.", List("tackling", "aggression", "workRate")),
    ("deep_lying_playmaker", "Deep-lying playmaker", "Rozgrywający z głębi. Kluczowe: passing, vision, decisions.", List("passing", "vision", "decisions")),
    ("box_to_box", "Box-to-box", "Pomocnik biegający całe boisko. Kluczowe: stamina, workRate, passing.", List("stamina", "workRate", "passing")),
    ("mezzala", "Mezzala", "Pomocnik boczny z wejściami do środka. Kluczowe: dribbling, passing, offTheBall.", List("dribbling", "passing", "offTheBall")),
    ("advanced_playmaker", "Advanced playmaker", "Rozgrywający pod polem karnym. Kluczowe: passing, vision, flair.", List("passing", "vision", "flair")),
    ("winger", "Skrzydłowy", "Klasyczny skrzydłowy. Kluczowe: pace, crossing, dribbling.", List("pace", "crossing", "dribbling")),
    ("inside_forward", "Inside forward", "Skrzydłowy wchodzący do środka. Kluczowe: shooting, dribbling, offTheBall.", List("shooting", "dribbling", "offTheBall")),
    ("raumdeuter", "Raumdeuter", "Szuka przestrzeni bez piłki. Kluczowe: offTheBall, anticipation, finishing.", List("offTheBall", "anticipation", "shooting")),
    ("target_man", "Target man", "Napastnik opierający grę o plecy. Kluczowe: strength, heading, firstTouch.", List("strength", "heading", "firstTouch")),
    ("poacher", "Poacher", "Gole w polu karnym. Kluczowe: finishing, anticipation, offTheBall.", List("shooting", "anticipation", "offTheBall")),
    ("pressing_forward", "Pressing forward", "Napastnik pressujący obronę. Kluczowe: workRate, aggression, stamina.", List("workRate", "aggression", "stamina")),
    ("false_nine", "False nine", "Środkowy cofnięty, wiąże obronę. Kluczowe: passing, vision, firstTouch.", List("passing", "vision", "firstTouch"))
  )
  val AllRoles: List[(String, String)] = RoleInfo.map { case (id, label, _, _) => (id, label) }
  def roleIdToLabel(id: String): String = AllRoles.find(_._1 == id).map(_._2).getOrElse(id)
  def roleDescription(id: String): String = RoleInfo.find(_._1 == id).map(_._3).getOrElse("")
  def roleKeyAttributes(id: String): List[String] = RoleInfo.find(_._1 == id).map(_._4).getOrElse(Nil)

  /** Domyślna rola dla slotu pozycji (FM-style). */
  private val defaultBySlot: Map[String, String] = Map(
    "GK" -> "goalkeeper",
    "LB" -> "full_back",
    "RB" -> "full_back",
    "LWB" -> "wing_back",
    "RWB" -> "wing_back",
    "LCB" -> "cb_stopper",
    "CB" -> "cb_stopper",
    "RCB" -> "cb_stopper",
    "CDM" -> "anchor",
    "LDM" -> "anchor",
    "RDM" -> "anchor",
    "CM" -> "box_to_box",
    "LCM" -> "box_to_box",
    "RCM" -> "box_to_box",
    "CAM" -> "advanced_playmaker",
    "LM" -> "winger",
    "RM" -> "winger",
    "LW" -> "inside_forward",
    "RW" -> "inside_forward",
    "ST" -> "poacher",
    "LST" -> "poacher",
    "RST" -> "poacher"
  )

  def defaultRoleForSlot(slot: String): Option[String] =
    defaultBySlot.get(slot)

  /** Domyślna rola na podstawie listy preferowanych pozycji zawodnika. */
  def defaultRoleForPositions(positions: List[String]): Option[String] =
    positions.collectFirst(Function.unlift(p => defaultBySlot.get(p)))
}
