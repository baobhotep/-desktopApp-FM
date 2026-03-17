package fmgame.shared.domain

enum SeasonPhase:
  case Setup
  case InProgress
  case Finished

enum MatchStatus:
  case Scheduled
  case Played
  case Postponed

enum InvitationStatus:
  case Pending
  case Accepted
  case Declined
  case Expired

enum TransferWindowStatus:
  case Open
  case Closed

enum TransferOfferStatus:
  case Pending
  case Accepted
  case Rejected
  case Withdrawn
  case Countered

enum MatchSquadSource:
  case Manual
  case Default
  case Bot
