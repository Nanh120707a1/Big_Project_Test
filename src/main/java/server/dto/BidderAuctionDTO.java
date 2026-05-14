package server.dto;

import model.Auction.AuctionStatus;

public class BidderAuctionDTO {
    private String auctionId;
    private String itemName;
    private String description;
    private String imageUrl;
    private double currentPrice;
    private AuctionStatus status;

    public BidderAuctionDTO(String auctionId, String itemName, String description,
                            String imageUrl, double currentPrice,
                            AuctionStatus status) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.description = description;
        this.imageUrl = imageUrl;
        this.currentPrice = currentPrice;
        this.status = status;
    }
}