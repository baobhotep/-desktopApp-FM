package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant

trait TransferOfferRepository {
  def create(offer: TransferOffer): ConnectionIO[Unit]
  def findById(id: TransferOfferId): ConnectionIO[Option[TransferOffer]]
  def update(offer: TransferOffer): ConnectionIO[Unit]
  def listByTeam(teamId: TeamId): ConnectionIO[List[TransferOffer]]
  def listByLeague(leagueId: LeagueId): ConnectionIO[List[TransferOffer]]
  def listPendingByPlayerAndWindow(playerId: PlayerId, windowId: TransferWindowId): ConnectionIO[List[TransferOffer]]
}

object TransferOfferRepository {
  def impl: TransferOfferRepository = new TransferOfferRepository {
    def create(offer: TransferOffer): ConnectionIO[Unit] =
      sql"""
        INSERT INTO transfer_offers (id, window_id, from_team_id, to_team_id, player_id, amount, status, created_at, responded_at)
        VALUES (${offer.id.value}, ${offer.windowId.value}, ${offer.fromTeamId.value}, ${offer.toTeamId.value},
          ${offer.playerId.value}, ${offer.amount}, ${offer.status.toString}, ${offer.createdAt}, ${offer.respondedAt})
      """.update.run.map(_ => ())

    def findById(id: TransferOfferId): ConnectionIO[Option[TransferOffer]] =
      sql"""
        SELECT id, window_id, from_team_id, to_team_id, player_id, amount, status, created_at, responded_at
        FROM transfer_offers WHERE id = ${id.value}
      """.query[(String, String, String, String, String, BigDecimal, String, Instant, Option[Instant])].option.map {
        _.map { case (id, wid, fid, tid, pid, amt, st, ca, ra) =>
          TransferOffer(
            TransferOfferId(id), TransferWindowId(wid), TeamId(fid), TeamId(tid), PlayerId(pid),
            amt, EnumParse.transferOfferStatus(st), ca, ra
          )
        }
      }

    def update(offer: TransferOffer): ConnectionIO[Unit] =
      sql"""
        UPDATE transfer_offers SET status = ${offer.status.toString}, responded_at = ${offer.respondedAt}
        WHERE id = ${offer.id.value}
      """.update.run.map(_ => ())

    def listByTeam(teamId: TeamId): ConnectionIO[List[TransferOffer]] =
      sql"""
        SELECT id, window_id, from_team_id, to_team_id, player_id, amount, status, created_at, responded_at
        FROM transfer_offers WHERE from_team_id = ${teamId.value} OR to_team_id = ${teamId.value}
        ORDER BY created_at DESC
      """.query[(String, String, String, String, String, BigDecimal, String, Instant, Option[Instant])].to[List].map {
        _.map { case (id, wid, fid, tid, pid, amt, st, ca, ra) =>
          TransferOffer(
            TransferOfferId(id), TransferWindowId(wid), TeamId(fid), TeamId(tid), PlayerId(pid),
            amt, EnumParse.transferOfferStatus(st), ca, ra
          )
        }
      }

    def listByLeague(leagueId: LeagueId): ConnectionIO[List[TransferOffer]] =
      sql"""
        SELECT o.id, o.window_id, o.from_team_id, o.to_team_id, o.player_id, o.amount, o.status, o.created_at, o.responded_at
        FROM transfer_offers o
        JOIN transfer_windows w ON o.window_id = w.id
        WHERE w.league_id = ${leagueId.value}
        ORDER BY o.created_at DESC
      """.query[(String, String, String, String, String, BigDecimal, String, Instant, Option[Instant])].to[List].map {
        _.map { case (id, wid, fid, tid, pid, amt, st, ca, ra) =>
          TransferOffer(
            TransferOfferId(id), TransferWindowId(wid), TeamId(fid), TeamId(tid), PlayerId(pid),
            amt, EnumParse.transferOfferStatus(st), ca, ra
          )
        }
      }

    def listPendingByPlayerAndWindow(playerId: PlayerId, windowId: TransferWindowId): ConnectionIO[List[TransferOffer]] =
      sql"""
        SELECT id, window_id, from_team_id, to_team_id, player_id, amount, status, created_at, responded_at
        FROM transfer_offers
        WHERE player_id = ${playerId.value} AND window_id = ${windowId.value} AND status = 'Pending'
      """.query[(String, String, String, String, String, BigDecimal, String, Instant, Option[Instant])].to[List].map {
        _.map { case (id, wid, fid, tid, pid, amt, st, ca, ra) =>
          TransferOffer(
            TransferOfferId(id), TransferWindowId(wid), TeamId(fid), TeamId(tid), PlayerId(pid),
            amt, EnumParse.transferOfferStatus(st), ca, ra
          )
        }
      }
  }
}
