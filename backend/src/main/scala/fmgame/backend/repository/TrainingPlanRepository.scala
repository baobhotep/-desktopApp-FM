package fmgame.backend.repository

import doobie.*
import doobie.implicits.*
import doobie.implicits.javatime.*
import fmgame.shared.domain.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.auto.*
import java.time.Instant

case class TeamTrainingPlan(teamId: TeamId, week: List[String], updatedAt: Instant)

trait TrainingPlanRepository {
  def findByTeamId(teamId: TeamId): ConnectionIO[Option[TeamTrainingPlan]]
  def upsert(teamId: TeamId, week: List[String], updatedAt: Instant): ConnectionIO[TeamTrainingPlan]
}

object TrainingPlanRepository {
  def impl: TrainingPlanRepository = new TrainingPlanRepository {
    def findByTeamId(teamId: TeamId): ConnectionIO[Option[TeamTrainingPlan]] =
      sql"""
        SELECT team_id, week, updated_at
        FROM team_training_plans
        WHERE team_id = ${teamId.value}
      """.query[(String, String, Instant)].option.map {
        _.map { case (tid, weekJson, at) =>
          val week = decode[List[String]](Option(weekJson).getOrElse("[]")).toOption.getOrElse(Nil)
          TeamTrainingPlan(TeamId(tid), week, at)
        }
      }

    def upsert(teamId: TeamId, week: List[String], updatedAt: Instant): ConnectionIO[TeamTrainingPlan] = {
      val weekJson = week.asJson.noSpaces
      sql"""
        MERGE INTO team_training_plans (team_id, week, updated_at)
        KEY (team_id)
        VALUES (${teamId.value}, $weekJson, ${updatedAt})
      """.update.run.map(_ => TeamTrainingPlan(teamId, week, updatedAt))
    }
  }
}

