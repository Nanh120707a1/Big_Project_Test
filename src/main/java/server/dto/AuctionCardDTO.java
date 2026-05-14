package server.dto;
public class AuctionCardDTO {
    private String auctionId;
    private String itemName;
    private String imageUrl;
    private double currentPrice;

    public AuctionCardDTO(String auctionId, String itemName, String imageUrl, double currentPrice) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.imageUrl = imageUrl;
        this.currentPrice = currentPrice;
    }

    // getters
}