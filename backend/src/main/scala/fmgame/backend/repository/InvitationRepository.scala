package fmgame.backend.repository

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import java.time.Instant

trait InvitationRepository {
  def create(inv: Invitation): ConnectionIO[Unit]
  def findByToken(token: String): ConnectionIO[Option[Invitation]]
  def update(inv: Invitation): ConnectionIO[Unit]
  def listPendingByInvitedUser(userId: UserId): ConnectionIO[List[Invitation]]
}

object InvitationRepository {
  def impl: InvitationRepository = new InvitationRepository {
    def create(inv: Invitation): ConnectionIO[Unit] =
      sql"""
        INSERT INTO invitations (id, league_id, invited_user_id, invited_by_user_id, token, status, created_at, expires_at)
        VALUES (${inv.id.value}, ${inv.leagueId.value}, ${inv.invitedUserId.value}, ${inv.invitedByUserId.value},
          ${inv.token}, ${inv.status.toString}, ${inv.createdAt}, ${inv.expiresAt})
      """.update.run.map(_ => ())

    def findByToken(token: String): ConnectionIO[Option[Invitation]] =
      sql"""
        SELECT id, league_id, invited_user_id, invited_by_user_id, token, status, created_at, expires_at
        FROM invitations WHERE token = $token
      """.query[(String, String, String, String, String, String, Instant, Instant)].option.map {
        _.map { case (id, lid, iu, ib, t, st, ca, ea) =>
          Invitation(InvitationId(id), LeagueId(lid), UserId(iu), UserId(ib), t, EnumParse.invitationStatus(st), ca, ea)
        }
      }

    def update(inv: Invitation): ConnectionIO[Unit] =
      sql"""
        UPDATE invitations SET status = ${inv.status.toString} WHERE id = ${inv.id.value}
      """.update.run.map(_ => ())

    def listPendingByInvitedUser(userId: UserId): ConnectionIO[List[Invitation]] =
      sql"""
        SELECT id, league_id, invited_user_id, invited_by_user_id, token, status, created_at, expires_at
        FROM invitations WHERE invited_user_id = ${userId.value} AND status = 'Pending'
        ORDER BY created_at DESC
      """.query[(String, String, String, String, String, String, Instant, Instant)].to[List].map {
        _.map { case (id, lid, iu, ib, t, st, ca, ea) =>
          Invitation(InvitationId(id), LeagueId(lid), UserId(iu), UserId(ib), t, EnumParse.invitationStatus(st), ca, ea)
        }
      }
  }
}
