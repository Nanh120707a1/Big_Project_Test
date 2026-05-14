package server.dto;

import java.time.LocalDateTime;

public class BidHistoryDTO {
    private String username;
    private double amount;
    private LocalDateTime timestamp;

    public BidHistoryDTO(String username, double amount, LocalDateTime timestamp) {
        this.username = username;
        this.amount = amount;
        this.timestamp = timestamp;
    }
}