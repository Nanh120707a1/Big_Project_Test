package server.dto;

import model.Auction.AuctionStatus;
import model.Auction.CancelReason;

import java.time.LocalDateTime;

public class SellerAuctionDTO {
    private String auctionId;
    private String itemName;
    private String imageUrl;
    private double price;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;
    private CancelReason cancelReason;

    public SellerAuctionDTO(String auctionId, String itemName, String imageUrl,
                            double price, LocalDateTime startTime,
                            LocalDateTime endTime, AuctionStatus status,
                            CancelReason cancelReason) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.imageUrl = imageUrl;
        this.price = price;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.cancelReason = cancelReason;
    }
}