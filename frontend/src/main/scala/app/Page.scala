package app

/** ADT for frontend routing. Replaces tuple-based navigation. */
enum Page:
  case Login
  case Register
  case Dashboard
  case LeagueView(leagueId: String)
  case TeamView(teamId: String, leagueId: String)
  case MatchView(matchId: String, leagueId: String, teamIdOpt: Option[String])
  case LineupEditor(matchId: String, teamId: String, leagueId: String)
  case AcceptInvitation(token: String)
