package app

import fmgame.shared.api.PlayerDto
import io.circe.{Decoder, HCursor}

/** Dekoder PlayerDto tolerujący brak pól physical/technical/mental/traits (domyślnie pusta mapa). */
object PlayerDtoCodec {
  private def optMapInt(c: HCursor, key: String): Decoder.Result[Map[String, Int]] =
    c.get[Map[String, Int]](key).orElse(Right(Map.empty))

  private def optDouble(c: HCursor, key: String): Decoder.Result[Double] =
    c.get[Double](key).orElse(Right(0.0))
  private def optDoubleDefault1(c: HCursor, key: String): Decoder.Result[Double] =
    c.get[Double](key).orElse(Right(1.0))

  implicit val decodePlayerDto: Decoder[PlayerDto] = (c: HCursor) =>
    for {
      id                 <- c.get[String]("id")
      teamId             <- c.get[String]("teamId")
      firstName          <- c.get[String]("firstName")
      lastName           <- c.get[String]("lastName")
      preferredPositions <- c.get[List[String]]("preferredPositions")
      injury             <- c.get[Option[String]]("injury")
      freshness          <- c.get[Double]("freshness")
      morale             <- c.get[Double]("morale")
      physical           <- optMapInt(c, "physical")
      technical          <- optMapInt(c, "technical")
      mental             <- optMapInt(c, "mental")
      traits             <- optMapInt(c, "traits")
      overall            <- optDouble(c, "overall")
      physicalAvg        <- optDouble(c, "physicalAvg")
      technicalAvg       <- optDouble(c, "technicalAvg")
      mentalAvg          <- optDouble(c, "mentalAvg")
      defenseAvg         <- optDouble(c, "defenseAvg")
      condition          <- optDoubleDefault1(c, "condition")
      matchSharpness     <- optDoubleDefault1(c, "matchSharpness")
    } yield PlayerDto(
      id, teamId, firstName, lastName, preferredPositions, injury, freshness, morale,
      physical, technical, mental, traits, overall, physicalAvg, technicalAvg, mentalAvg, defenseAvg,
      condition.max(0).min(1), matchSharpness.max(0).min(1)
    )
}
