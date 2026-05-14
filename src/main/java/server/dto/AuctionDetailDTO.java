package server.dto;

import java.util.List;

public class AuctionDetailDTO {
    private String auctionId;
    private String itemName;
    private String description;
    private String imageUrl;
    private double currentPrice;
    private List<BidHistoryDTO> bidHistory;

    public AuctionDetailDTO(String auctionId, String itemName, String description,
                            String imageUrl, double currentPrice,
                            List<BidHistoryDTO> bidHistory) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.description = description;
        this.imageUrl = imageUrl;
        this.currentPrice = currentPrice;
        this.bidHistory = bidHistory;
    }
}