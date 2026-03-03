package fmgame.backend.repository

import doobie.*
import doobie.implicits.*
import zio.Task
import zio.ZIO
import zio.interop.catz.*
import java.time.Instant
import java.time.LocalDate

object Database {

  /** Inicjalizacja schematu bazy. Transactor tworzony w Main (ZIO). */
  def initSchema(xa: Transactor[Task]): Task[Unit] = {
    val createUsers =
      sql"""
        CREATE TABLE IF NOT EXISTS users (
          id VARCHAR(36) PRIMARY KEY,
          email VARCHAR(255) UNIQUE NOT NULL,
          password_hash VARCHAR(255) NOT NULL,
          display_name VARCHAR(255) NOT NULL,
          created_at TIMESTAMP NOT NULL
        )
      """.update.run

    val createLeagues =
      sql"""
        CREATE TABLE IF NOT EXISTS leagues (
          id VARCHAR(36) PRIMARY KEY,
          name VARCHAR(255) NOT NULL,
          team_count INT NOT NULL,
          current_matchday INT NOT NULL,
          total_matchdays INT NOT NULL,
          season_phase VARCHAR(32) NOT NULL,
          home_advantage DOUBLE NOT NULL,
          start_date DATE,
          created_by_user_id VARCHAR(36) NOT NULL,
          created_at TIMESTAMP NOT NULL,
          timezone VARCHAR(64) NOT NULL
        )
      """.update.run

    val createTeams =
      sql"""
        CREATE TABLE IF NOT EXISTS teams (
          id VARCHAR(36) PRIMARY KEY,
          league_id VARCHAR(36) NOT NULL,
          name VARCHAR(255) NOT NULL,
          owner_type VARCHAR(16) NOT NULL,
          owner_user_id VARCHAR(36),
          owner_bot_id VARCHAR(36),
          budget DECIMAL(20,2) NOT NULL,
          default_game_plan_id VARCHAR(36),
          created_at TIMESTAMP NOT NULL,
          elo_rating DOUBLE DEFAULT 1500.0 NOT NULL,
          manager_name VARCHAR(255),
          FOREIGN KEY (league_id) REFERENCES leagues(id)
        )
      """.update.run

    val createInvitations =
      sql"""
        CREATE TABLE IF NOT EXISTS invitations (
          id VARCHAR(36) PRIMARY KEY,
          league_id VARCHAR(36) NOT NULL,
          invited_user_id VARCHAR(36) NOT NULL,
          invited_by_user_id VARCHAR(36) NOT NULL,
          token VARCHAR(64) UNIQUE NOT NULL,
          status VARCHAR(32) NOT NULL,
          created_at TIMESTAMP NOT NULL,
          expires_at TIMESTAMP NOT NULL,
          FOREIGN KEY (league_id) REFERENCES leagues(id)
        )
      """.update.run

    val createPlayers =
      sql"""
        CREATE TABLE IF NOT EXISTS players (
          id VARCHAR(36) PRIMARY KEY,
          team_id VARCHAR(36) NOT NULL,
          first_name VARCHAR(128) NOT NULL,
          last_name VARCHAR(128) NOT NULL,
          preferred_positions VARCHAR(512),
          physical CLOB,
          technical CLOB,
          mental CLOB,
          traits CLOB,
          body_params CLOB,
          injury CLOB,
          freshness DOUBLE NOT NULL,
          morale DOUBLE NOT NULL,
          created_at TIMESTAMP NOT NULL,
          FOREIGN KEY (team_id) REFERENCES teams(id)
        )
      """.update.run

    val createReferees =
      sql"""
        CREATE TABLE IF NOT EXISTS referees (
          id VARCHAR(36) PRIMARY KEY,
          league_id VARCHAR(36) NOT NULL,
          name VARCHAR(128) NOT NULL,
          strictness DOUBLE NOT NULL,
          FOREIGN KEY (league_id) REFERENCES leagues(id)
        )
      """.update.run

    val createMatches =
      sql"""
        CREATE TABLE IF NOT EXISTS matches (
          id VARCHAR(36) PRIMARY KEY,
          league_id VARCHAR(36) NOT NULL,
          matchday INT NOT NULL,
          home_team_id VARCHAR(36) NOT NULL,
          away_team_id VARCHAR(36) NOT NULL,
          scheduled_at TIMESTAMP NOT NULL,
          status VARCHAR(32) NOT NULL,
          home_goals INT,
          away_goals INT,
          referee_id VARCHAR(36) NOT NULL,
          result_log_id VARCHAR(36),
          FOREIGN KEY (league_id) REFERENCES leagues(id),
          FOREIGN KEY (home_team_id) REFERENCES teams(id),
          FOREIGN KEY (away_team_id) REFERENCES teams(id),
          FOREIGN KEY (referee_id) REFERENCES referees(id)
        )
      """.update.run

    val createMatchResultLogs =
      sql"""
        CREATE TABLE IF NOT EXISTS match_result_logs (
          id VARCHAR(36) PRIMARY KEY,
          match_id VARCHAR(36) UNIQUE NOT NULL,
          events CLOB,
          summary CLOB,
          created_at TIMESTAMP NOT NULL,
          FOREIGN KEY (match_id) REFERENCES matches(id)
        )
      """.update.run

    val createMatchSquads =
      sql"""
        CREATE TABLE IF NOT EXISTS match_squads (
          id VARCHAR(36) PRIMARY KEY,
          match_id VARCHAR(36) NOT NULL,
          team_id VARCHAR(36) NOT NULL,
          lineup CLOB NOT NULL,
          game_plan CLOB NOT NULL,
          submitted_at TIMESTAMP NOT NULL,
          source VARCHAR(32) NOT NULL,
          UNIQUE(match_id, team_id),
          FOREIGN KEY (match_id) REFERENCES matches(id),
          FOREIGN KEY (team_id) REFERENCES teams(id)
        )
      """.update.run

    val createTransferWindows =
      sql"""
        CREATE TABLE IF NOT EXISTS transfer_windows (
          id VARCHAR(36) PRIMARY KEY,
          league_id VARCHAR(36) NOT NULL,
          open_after_matchday INT NOT NULL,
          close_before_matchday INT NOT NULL,
          status VARCHAR(32) NOT NULL,
          FOREIGN KEY (league_id) REFERENCES leagues(id)
        )
      """.update.run

    val createTransferOffers =
      sql"""
        CREATE TABLE IF NOT EXISTS transfer_offers (
          id VARCHAR(36) PRIMARY KEY,
          window_id VARCHAR(36) NOT NULL,
          from_team_id VARCHAR(36) NOT NULL,
          to_team_id VARCHAR(36) NOT NULL,
          player_id VARCHAR(36) NOT NULL,
          amount DECIMAL(20,2) NOT NULL,
          status VARCHAR(32) NOT NULL,
          created_at TIMESTAMP NOT NULL,
          responded_at TIMESTAMP,
          FOREIGN KEY (window_id) REFERENCES transfer_windows(id)
        )
      """.update.run

    val createLeagueContexts =
      sql"""
        CREATE TABLE IF NOT EXISTS league_contexts (
          id VARCHAR(36) PRIMARY KEY,
          league_id VARCHAR(36) UNIQUE NOT NULL,
          position_stats CLOB,
          created_at TIMESTAMP NOT NULL,
          FOREIGN KEY (league_id) REFERENCES leagues(id)
        )
      """.update.run

    val createGamePlanSnapshots =
      sql"""
        CREATE TABLE IF NOT EXISTS game_plan_snapshots (
          id VARCHAR(36) PRIMARY KEY,
          team_id VARCHAR(36) NOT NULL,
          name VARCHAR(255) NOT NULL,
          game_plan CLOB NOT NULL,
          created_at TIMESTAMP NOT NULL,
          FOREIGN KEY (team_id) REFERENCES teams(id)
        )
      """.update.run

    val createTrainingPlans =
      sql"""
        CREATE TABLE IF NOT EXISTS team_training_plans (
          team_id VARCHAR(36) PRIMARY KEY,
          week CLOB NOT NULL,
          updated_at TIMESTAMP NOT NULL,
          FOREIGN KEY (team_id) REFERENCES teams(id)
        )
      """.update.run

    val createTeamShortlists =
      sql"""
        CREATE TABLE IF NOT EXISTS team_shortlists (
          team_id VARCHAR(36) NOT NULL,
          player_id VARCHAR(36) NOT NULL,
          created_at TIMESTAMP NOT NULL,
          PRIMARY KEY(team_id, player_id),
          FOREIGN KEY (team_id) REFERENCES teams(id),
          FOREIGN KEY (player_id) REFERENCES players(id)
        )
      """.update.run

    val createScoutingReports =
      sql"""
        CREATE TABLE IF NOT EXISTS scouting_reports (
          id VARCHAR(36) PRIMARY KEY,
          team_id VARCHAR(36) NOT NULL,
          player_id VARCHAR(36) NOT NULL,
          rating DOUBLE NOT NULL,
          notes CLOB NOT NULL,
          created_at TIMESTAMP NOT NULL,
          FOREIGN KEY (team_id) REFERENCES teams(id),
          FOREIGN KEY (player_id) REFERENCES players(id)
        )
      """.update.run

    val createLeaguePlayerMatchStats =
      sql"""
        CREATE TABLE IF NOT EXISTS league_player_match_stats (
          league_id VARCHAR(36) NOT NULL,
          match_id VARCHAR(36) NOT NULL,
          player_id VARCHAR(36) NOT NULL,
          team_id VARCHAR(36) NOT NULL,
          goals INT NOT NULL DEFAULT 0,
          assists INT NOT NULL DEFAULT 0,
          minutes_played INT NOT NULL DEFAULT 0,
          PRIMARY KEY (match_id, player_id),
          FOREIGN KEY (league_id) REFERENCES leagues(id),
          FOREIGN KEY (match_id) REFERENCES matches(id),
          FOREIGN KEY (player_id) REFERENCES players(id),
          FOREIGN KEY (team_id) REFERENCES teams(id)
        )
      """.update.run

    val addEloColumn =
      sql"ALTER TABLE teams ADD COLUMN elo_rating DOUBLE DEFAULT 1500 NOT NULL".update.run
    val addManagerNameColumn =
      sql"ALTER TABLE teams ADD COLUMN manager_name VARCHAR(255)".update.run

    val steps = for {
      _ <- createUsers
      _ <- createLeagues
      _ <- createTeams
      _ <- createInvitations
      _ <- createPlayers
      _ <- createReferees
      _ <- createMatches
      _ <- createMatchResultLogs
      _ <- createMatchSquads
      _ <- createTransferWindows
      _ <- createTransferOffers
      _ <- createLeagueContexts
      _ <- createGamePlanSnapshots
      _ <- createTrainingPlans
      _ <- createTeamShortlists
      _ <- createScoutingReports
      _ <- createLeaguePlayerMatchStats
    } yield ()

    (steps.transact(xa) *> addEloColumn.transact(xa).unit *> addManagerNameColumn.transact(xa).unit).catchAll(_ => ZIO.unit)
  }
}
