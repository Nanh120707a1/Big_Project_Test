package server.service;

import model.Auction.Auction;
import model.Auction.AuctionStatus;
import model.item.Item;
import server.dao.AuctionRepository;
import server.dao.BidTransactionRepository;
import server.dao.ItemRepository;
import server.dto.BidderAuctionDTO;

import java.util.*;

public class BidderService {

    private final BidTransactionRepository bidRepo;
    private final AuctionRepository auctionRepo;
    private final ItemRepository itemRepo;

    public BidderService(BidTransactionRepository bidRepo,
                         AuctionRepository auctionRepo,
                         ItemRepository itemRepo) {
        this.bidRepo = bidRepo;
        this.auctionRepo = auctionRepo;
        this.itemRepo = itemRepo;
    }

    // đang tham gia
    public List<BidderAuctionDTO> getParticipatedAuctions(String userId) {

        List<String> auctionIds = bidRepo.findAuctionIdsByBidder(userId);

        return auctionIds.stream()
                .map(id -> {
                    Auction a = auctionRepo.findById(id).orElseThrow();
                    Item item = itemRepo.findById(a.getItemId()).orElseThrow();

                    return new BidderAuctionDTO(
                            a.getId(),
                            item.getItemName(),
                            item.getItemDescription(),
                            item.getImageUrl(),
                            a.getCurrentPrice(),
                            a.getStatus()
                    );
                }).toList();
    }

    // đã thắng
    public List<BidderAuctionDTO> getWonAuctions(String userId) {

        return auctionRepo.findAll().stream()
                .filter(a -> userId.equals(a.getLeadingBidderId()))
                .filter(a -> a.getStatus() == AuctionStatus.FINISHED)
                .map(a -> {
                    Item item = itemRepo.findById(a.getItemId()).orElseThrow();

                    return new BidderAuctionDTO(
                            a.getId(),
                            item.getItemName(),
                            item.getItemDescription(),
                            item.getImageUrl(),
                            a.getCurrentPrice(),
                            a.getStatus()
                    );
                }).toList();
    }
}