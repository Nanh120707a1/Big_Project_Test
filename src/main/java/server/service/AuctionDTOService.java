package server.service;

import model.Auction.Auction;
import model.Auction.AuctionStatus;
import model.item.Item;
import model.user.User;
import server.dao.AuctionRepository;
import server.dao.BidTransactionRepository;
import server.dao.ItemRepository;
import server.dao.UserRepository;
import server.dto.AuctionCardDTO;
import server.dto.AuctionDetailDTO;
import server.dto.BidHistoryDTO;

import java.util.*;

public class AuctionDTOService {

    private final AuctionRepository auctionRepo;
    private final ItemRepository itemRepo;
    private final BidTransactionRepository bidRepo;
    private final UserRepository userRepo;

    public AuctionDTOService(AuctionRepository auctionRepo,
                          ItemRepository itemRepo,
                          BidTransactionRepository bidRepo,
                          UserRepository userRepo) {
        this.auctionRepo = auctionRepo;
        this.itemRepo = itemRepo;
        this.bidRepo = bidRepo;
        this.userRepo = userRepo;
    }

    // MAIN VIEW
    public List<AuctionCardDTO> getActiveAuctions() {
        List<AuctionStatus> statuses = List.of(
                AuctionStatus.OPEN,
                AuctionStatus.RUNNING,
                AuctionStatus.FINISHED
        );

        return auctionRepo.findByStatus(statuses)
                .stream()
                .map(a -> {
                    Item item = itemRepo.findById(a.getItemId()).orElseThrow();

                    return new AuctionCardDTO(
                            a.getId(),
                            item.getItemName(),
                            item.getImageUrl(),
                            a.getCurrentPrice()
                    );
                }).toList();
    }

    // DETAIL VIEW
    public AuctionDetailDTO getAuctionDetail(String auctionId) {
        Auction auction = auctionRepo.findById(auctionId).orElseThrow();
        Item item = itemRepo.findById(auction.getItemId()).orElseThrow();

        List<BidHistoryDTO> history = bidRepo.findByAuctionId(auctionId)
                .stream()
                .map(b -> {
                    User u = userRepo.findById(b.getBidderId()).orElseThrow();
                    return new BidHistoryDTO(
                            u.getUsername(),
                            b.getAmount(),
                            b.getTimestamp()
                    );
                }).toList();

        return new AuctionDetailDTO(
                auction.getId(),
                item.getItemName(),
                item.getItemDescription(),
                item.getImageUrl(),
                auction.getCurrentPrice(),
                history
        );
    }
}