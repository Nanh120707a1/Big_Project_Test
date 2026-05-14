package server.dto;

import java.time.LocalDateTime;

public class AdminAuctionDTO {
    private String auctionId;
    private String itemName;
    private String description;
    private String imageUrl;
    private double itemOriginalPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public AdminAuctionDTO(String auctionId, String itemName, String description,
                           String imageUrl, double itemOriginalPrice,
                           LocalDateTime startTime, LocalDateTime endTime) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.description = description;
        this.imageUrl = imageUrl;
        this.itemOriginalPrice = itemOriginalPrice;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}