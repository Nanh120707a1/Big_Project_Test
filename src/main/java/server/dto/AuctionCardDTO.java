package server.dto;
import model.Auction.AuctionStatus;
import model.item.ItemType;

import java.time.LocalDateTime;

/**
 * AuctionCardDTO — dữ liệu hiển thị một phiên đấu giá trên MainView.
 *
 * Được tạo bằng JOIN query (auctions JOIN items) trong AuctionRepository.
 * MainView KHÔNG dùng Auction entity trực tiếp — luôn dùng DTO này.
 *
 * Không chứa logic business — chỉ là data carrier (immutable record).
 */
public record AuctionCardDTO(
        String        auctionId,
        String        itemId,
        String        itemName,
        String        description,
        String        imageUrl,
        ItemType      category,
        double        currentPrice,
        double        priceStep,
        String        leadingBidderId,   // null nếu chưa có ai bid
        LocalDateTime startTime,
        LocalDateTime endTime,
        AuctionStatus status
) {
    /**
     * Tính thời gian còn lại (giây) đến endTime.
     * Âm nếu phiên đã kết thúc.
     */
    public long secondsRemaining() {
        return java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
    }

    /** Giá bid tối thiểu tiếp theo hợp lệ. */
    public double minNextBid() {
        return currentPrice + priceStep;
    }

    public boolean isActive() {
        return status == AuctionStatus.RUNNING;
    }
}