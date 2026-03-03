package fmgame.shared.domain

/** FORMACJE §13.4: Konfiguracja wrzutów z autu (defaultTaker, longThrowTaker, strefy, biegacze). Może być częścią gamePlanJson.
  * targetZones i runners: walidacja – elementy niepustych list muszą być non-empty string. */
case class ThrowInConfig(
  defaultTakerPlayerId: Option[String] = None,
  longThrowTakerPlayerId: Option[String] = None,
  shortOption: Boolean = true,
  targetZones: List[String] = Nil,
  runners: List[String] = Nil,
  useLongThrow: Boolean = false
) {
  /** Zwraca Some(błąd) jeśli listy zawierają nieprawidłowe elementy (puste stringi). */
  def validate: Option[String] = {
    if (targetZones.exists(_.trim.isEmpty)) return Some("targetZones cannot contain empty strings")
    if (runners.exists(_.trim.isEmpty)) return Some("runners cannot contain empty strings")
    None
  }
}
