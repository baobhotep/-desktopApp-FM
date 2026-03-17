package fmgame.backend.service

import fmgame.shared.domain.*
import fmgame.shared.api.*
import zio.*

trait TransferService {
  def getTransferWindows(leagueId: LeagueId): ZIO[Any, String, List[TransferWindowDto]]
  def getTransferWindowsForUser(leagueId: LeagueId, userId: UserId): ZIO[Any, String, List[TransferWindowDto]]
  def getTransferOffers(leagueId: LeagueId, teamIdOpt: Option[TeamId]): ZIO[Any, String, List[TransferOfferDto]]
  def getTransferOffersForUser(leagueId: LeagueId, teamIdOpt: Option[TeamId], userId: UserId): ZIO[Any, String, List[TransferOfferDto]]
  def createTransferOffer(leagueId: LeagueId, userId: UserId, req: CreateTransferOfferRequest): ZIO[Any, String, TransferOfferDto]
  def acceptTransferOffer(offerId: TransferOfferId, userId: UserId): ZIO[Any, String, Unit]
  def rejectTransferOffer(offerId: TransferOfferId, userId: UserId): ZIO[Any, String, Unit]
  def counterTransferOffer(offerId: TransferOfferId, userId: UserId, counterAmount: BigDecimal): ZIO[Any, String, TransferOfferDto]
}
