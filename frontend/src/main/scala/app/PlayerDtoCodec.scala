package app

import fmgame.shared.api.PlayerDto
import io.circe.{Decoder, HCursor}

/** Dekoder PlayerDto tolerujący brak pól physical/technical/mental/traits (domyślnie pusta mapa). */
object PlayerDtoCodec {
  private def optMapInt(c: HCursor, key: String): Decoder.Result[Map[String, Int]] =
    c.get[Map[String, Int]](key).orElse(Right(Map.empty))

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
    } yield PlayerDto(
      id, teamId, firstName, lastName, preferredPositions, injury, freshness, morale,
      physical, technical, mental, traits
    )
}
