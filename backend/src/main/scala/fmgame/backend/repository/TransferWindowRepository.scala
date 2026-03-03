package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import java.time.Instant

trait TransferWindowRepository {
  def create(tw: TransferWindow): ConnectionIO[Unit]
  def update(tw: TransferWindow): ConnectionIO[Unit]
  def listByLeague(leagueId: LeagueId): ConnectionIO[List[TransferWindow]]
}

object TransferWindowRepository {
  def impl: TransferWindowRepository = new TransferWindowRepository {
    def create(tw: TransferWindow): ConnectionIO[Unit] =
      sql"""
        INSERT INTO transfer_windows (id, league_id, open_after_matchday, close_before_matchday, status)
        VALUES (${tw.id.value}, ${tw.leagueId.value}, ${tw.openAfterMatchday}, ${tw.closeBeforeMatchday}, ${tw.status.toString})
      """.update.run.map(_ => ())

    def update(tw: TransferWindow): ConnectionIO[Unit] =
      sql"""
        UPDATE transfer_windows SET status = ${tw.status.toString}
        WHERE id = ${tw.id.value}
      """.update.run.map(_ => ())

    def listByLeague(leagueId: LeagueId): ConnectionIO[List[TransferWindow]] =
      sql"""
        SELECT id, league_id, open_after_matchday, close_before_matchday, status
        FROM transfer_windows WHERE league_id = ${leagueId.value} ORDER BY open_after_matchday
      """.query[(String, String, Int, Int, String)].to[List].map {
        _.map { case (id, lid, oam, cbm, st) =>
          TransferWindow(
            TransferWindowId(id), LeagueId(lid), oam, cbm,
            EnumParse.transferWindowStatus(st)
          )
        }
      }
  }
}
