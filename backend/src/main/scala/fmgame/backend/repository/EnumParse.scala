package fmgame.backend.repository

import fmgame.backend.domain.TeamOwnerType
import fmgame.shared.domain.*

object EnumParse {
  def teamOwnerType(s: String): TeamOwnerType =
    teamOwnerTypeOption(s).getOrElse(throw new IllegalArgumentException(s"Invalid TeamOwnerType: $s"))
  def teamOwnerTypeOption(s: String): Option[TeamOwnerType] =
    TeamOwnerType.values.find(_.toString == s)

  def seasonPhase(s: String): SeasonPhase =
    seasonPhaseOption(s).getOrElse(throw new IllegalArgumentException(s"Invalid SeasonPhase: $s"))
  def seasonPhaseOption(s: String): Option[SeasonPhase] =
    SeasonPhase.values.find(_.toString == s)

  def matchStatus(s: String): MatchStatus =
    matchStatusOption(s).getOrElse(throw new IllegalArgumentException(s"Invalid MatchStatus: $s"))
  def matchStatusOption(s: String): Option[MatchStatus] =
    MatchStatus.values.find(_.toString == s)

  def invitationStatus(s: String): InvitationStatus =
    invitationStatusOption(s).getOrElse(throw new IllegalArgumentException(s"Invalid InvitationStatus: $s"))
  def invitationStatusOption(s: String): Option[InvitationStatus] =
    InvitationStatus.values.find(_.toString == s)

  def transferWindowStatus(s: String): TransferWindowStatus =
    transferWindowStatusOption(s).getOrElse(throw new IllegalArgumentException(s"Invalid TransferWindowStatus: $s"))
  def transferWindowStatusOption(s: String): Option[TransferWindowStatus] =
    TransferWindowStatus.values.find(_.toString == s)

  def transferOfferStatus(s: String): TransferOfferStatus =
    transferOfferStatusOption(s).getOrElse(throw new IllegalArgumentException(s"Invalid TransferOfferStatus: $s"))
  def transferOfferStatusOption(s: String): Option[TransferOfferStatus] =
    TransferOfferStatus.values.find(_.toString == s)

  def matchSquadSource(s: String): MatchSquadSource =
    matchSquadSourceOption(s).getOrElse(throw new IllegalArgumentException(s"Invalid MatchSquadSource: $s"))
  def matchSquadSourceOption(s: String): Option[MatchSquadSource] =
    MatchSquadSource.values.find(_.toString == s)
}
