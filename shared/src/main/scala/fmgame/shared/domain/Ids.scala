package fmgame.shared.domain

opaque type UserId = String
object UserId {
  def apply(s: String): UserId = s
  def random(): UserId = java.util.UUID.randomUUID().toString
  extension (x: UserId) def value: String = x
}

opaque type LeagueId = String
object LeagueId {
  def apply(s: String): LeagueId = s
  def random(): LeagueId = java.util.UUID.randomUUID().toString
  extension (x: LeagueId) def value: String = x
}

opaque type TeamId = String
object TeamId {
  def apply(s: String): TeamId = s
  def random(): TeamId = java.util.UUID.randomUUID().toString
  extension (x: TeamId) def value: String = x
}

opaque type PlayerId = String
object PlayerId {
  def apply(s: String): PlayerId = s
  def random(): PlayerId = java.util.UUID.randomUUID().toString
  extension (x: PlayerId) def value: String = x
}

opaque type MatchId = String
object MatchId {
  def apply(s: String): MatchId = s
  def random(): MatchId = java.util.UUID.randomUUID().toString
  extension (x: MatchId) def value: String = x
}

opaque type RefereeId = String
object RefereeId {
  def apply(s: String): RefereeId = s
  def random(): RefereeId = java.util.UUID.randomUUID().toString
  extension (x: RefereeId) def value: String = x
}

opaque type InvitationId = String
object InvitationId {
  def apply(s: String): InvitationId = s
  def random(): InvitationId = java.util.UUID.randomUUID().toString
  extension (x: InvitationId) def value: String = x
}

opaque type MatchSquadId = String
object MatchSquadId {
  def apply(s: String): MatchSquadId = s
  def random(): MatchSquadId = java.util.UUID.randomUUID().toString
  extension (x: MatchSquadId) def value: String = x
}

opaque type MatchResultLogId = String
object MatchResultLogId {
  def apply(s: String): MatchResultLogId = s
  def random(): MatchResultLogId = java.util.UUID.randomUUID().toString
  extension (x: MatchResultLogId) def value: String = x
}

opaque type GamePlanSnapshotId = String
object GamePlanSnapshotId {
  def apply(s: String): GamePlanSnapshotId = s
  def random(): GamePlanSnapshotId = java.util.UUID.randomUUID().toString
  extension (x: GamePlanSnapshotId) def value: String = x
}

opaque type TransferWindowId = String
object TransferWindowId {
  def apply(s: String): TransferWindowId = s
  def random(): TransferWindowId = java.util.UUID.randomUUID().toString
  extension (x: TransferWindowId) def value: String = x
}

opaque type TransferOfferId = String
object TransferOfferId {
  def apply(s: String): TransferOfferId = s
  def random(): TransferOfferId = java.util.UUID.randomUUID().toString
  extension (x: TransferOfferId) def value: String = x
}

opaque type ContractId = String
object ContractId {
  def apply(s: String): ContractId = s
  def random(): ContractId = java.util.UUID.randomUUID().toString
  extension (x: ContractId) def value: String = x
}

opaque type BotId = String
object BotId {
  def apply(s: String): BotId = s
  def random(): BotId = java.util.UUID.randomUUID().toString
  extension (x: BotId) def value: String = x
}

opaque type LeagueContextId = String
object LeagueContextId {
  def apply(s: String): LeagueContextId = s
  def random(): LeagueContextId = java.util.UUID.randomUUID().toString
  extension (x: LeagueContextId) def value: String = x
}
