package server.service;

import model.Auction.AuctionStatus;
import model.item.Item;
import server.dao.AuctionRepository;
import server.dao.ItemRepository;
import server.dto.SellerAuctionDTO;

import java.util.*;

public class SellerService {

    private final AuctionRepository auctionRepo;
    private final ItemRepository itemRepo;

    public SellerService(AuctionRepository auctionRepo, ItemRepository itemRepo) {
        this.auctionRepo = auctionRepo;
        this.itemRepo = itemRepo;
    }

    public List<SellerAuctionDTO> getSellerAuctions(String sellerId) {

        return auctionRepo.findBySellerId(sellerId)
                .stream()
                .map(a -> {
                    Item item = itemRepo.findById(a.getItemId()).orElseThrow();

                    double price;

                    if (a.getStatus() == AuctionStatus.PENDING ||
                            a.getStatus() == AuctionStatus.OPEN) {
                        price = item.getItemOriginalPrice();
                    } else {
                        price = a.getCurrentPrice();
                    }

                    return new SellerAuctionDTO(
                            a.getId(),
                            item.getItemName(),
                            item.getImageUrl(),
                            price,
                            a.getStartTime(),
                            a.getEndTime(),
                            a.getStatus(),
                            a.getCancelReason()
                    );
                }).toList();
    }
}