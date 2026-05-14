package server.dto;

import java.time.LocalDateTime;
/**
 * DTO dùng cho:
 *  - AuctionDetail screen
 *  - Bid history table
 *  - Bid visualization
 */
public class BidHistoryDTO {

    private final String bidderUsername;

    private final double amount;

    private final LocalDateTime bidTime;

    public BidHistoryDTO(String bidderUsername,
                         double amount,
                         LocalDateTime bidTime) {
        this.bidderUsername = bidderUsername;
        this.amount = amount;
        this.bidTime = bidTime;
    }

    public String getBidderUsername() {return bidderUsername;}
    public double getAmount() {return amount;}
    public LocalDateTime getBidTime() {return bidTime;}
}