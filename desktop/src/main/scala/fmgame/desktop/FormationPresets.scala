package fmgame.desktop

/** Presety formacji dla desktopu: nazwa → sloty, pozycje (x,y 0–1). Używane do generowania gamePlanJson. */
object FormationPresets {

  val Formation433: (String, List[String], List[(Double, Double)]) = (
    "4-3-3",
    List("GK", "LB", "LCB", "RCB", "RB", "LCM", "CDM", "RCM", "LW", "RW", "ST"),
    List((0.04, 0.5), (0.18, 0.15), (0.18, 0.38), (0.18, 0.62), (0.18, 0.85), (0.35, 0.30), (0.35, 0.50), (0.35, 0.70), (0.55, 0.20), (0.55, 0.80), (0.65, 0.50))
  )
  val Formation442: (String, List[String], List[(Double, Double)]) = (
    "4-4-2",
    List("GK", "LB", "CB", "CB", "RB", "LM", "LCM", "RCM", "RM", "ST", "ST"),
    List((0.04, 0.5), (0.18, 0.12), (0.18, 0.38), (0.18, 0.62), (0.18, 0.88), (0.38, 0.15), (0.38, 0.38), (0.38, 0.62), (0.38, 0.85), (0.62, 0.35), (0.62, 0.65))
  )
  val Formation4231: (String, List[String], List[(Double, Double)]) = (
    "4-2-3-1",
    List("GK", "LB", "CB", "CB", "RB", "LDM", "RDM", "LW", "CAM", "RW", "ST"),
    List((0.04, 0.5), (0.18, 0.15), (0.18, 0.38), (0.18, 0.62), (0.18, 0.85), (0.30, 0.35), (0.30, 0.65), (0.52, 0.18), (0.52, 0.5), (0.52, 0.82), (0.68, 0.5))
  )
  val Formation352: (String, List[String], List[(Double, Double)]) = (
    "3-5-2",
    List("GK", "LCB", "CB", "RCB", "LWB", "LCM", "CM", "RCM", "RWB", "LST", "RST"),
    List((0.04, 0.5), (0.18, 0.25), (0.18, 0.5), (0.18, 0.75), (0.32, 0.12), (0.38, 0.35), (0.38, 0.5), (0.38, 0.65), (0.32, 0.88), (0.62, 0.35), (0.62, 0.65))
  )

  val All: List[(String, List[String], List[(Double, Double)])] = List(Formation433, Formation442, Formation4231, Formation352)

  /** Buduje minimalny gamePlanJson: formationName + customPositions (11 par [x,y]). Opcjonalnie formacja w obronie. */
  def toGamePlanJson(
    formationName: String,
    positions: List[(Double, Double)],
    defenseFormationName: Option[String] = None,
    defensePositions: Option[List[(Double, Double)]] = None
  ): String = {
    val posArr = positions.take(11).map { case (x, y) => s"[${x.max(0).min(1)},${y.max(0).min(1)}]" }.mkString(",")
    val base = s"""{"formationName":"$formationName","customPositions":[$posArr]"""
    val defensePart = (defenseFormationName, defensePositions) match {
      case (Some(name), Some(pos)) if pos.size >= 11 =>
        val defArr = pos.take(11).map { case (x, y) => s"[${x.max(0).min(1)},${y.max(0).min(1)}]" }.mkString(",")
        s""","defenseFormationName":"$name","defenseCustomPositions":[$defArr]"""
      case (Some(name), _) => s""","defenseFormationName":"$name""""
      case _ => ""
    }
    s"$base$defensePart}"
  }
}
