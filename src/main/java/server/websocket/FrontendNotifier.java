package server.websocket;

import model.Auction.AuctionStatus;

/**
 * FrontendNotifier — gửi realtime update tới các client qua WebSocket.
 *
 * Thay đổi so với phiên bản trước:
 *  - Phụ thuộc vào WebSocketBroadcaster (interface) thay vì WebSocketServer (class cụ thể).
 *  - Team implement WebSocketBroadcaster phù hợp với thư viện đang dùng.
 *  - Dễ test: new FrontendNotifier(mockBroadcaster).
 *
 * FrontendNotifier chỉ biết GỬI CÁI GÌ.
 * WebSocketBroadcaster biết GỬI NHƯ THẾ NÀO.
 *
 * Gọi sau khi DB commit thành công — KHÔNG gọi trong transaction.
 */
public class FrontendNotifier {

    private final WebSocketBroadcaster broadcaster;

    public FrontendNotifier(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    // ──────────────────────────────────────────────
    // BID UPDATE
    // ──────────────────────────────────────────────

    /**
     * Broadcast bid mới tới tất cả client đang xem phiên.
     * Gọi bởi BidService.placeBid() SAU KHI transaction commit thành công.
     *
     * @param auctionId       ID phiên
     * @param bidderId        ID người vừa bid
     * @param newCurrentPrice Giá hiện tại mới
     */
    public void broadcastBidUpdate(String auctionId, String bidderId, double newCurrentPrice) {
        String message = buildJson("BID_UPDATE",
                "auctionId", auctionId,
                "bidderId",  bidderId,
                "price",     String.valueOf(newCurrentPrice));

        sendSafely(auctionId, message);
        System.out.printf("[FrontendNotifier] BID_UPDATE → phiên=%s | bidder=%s | giá=%.0f%n",
                auctionId, bidderId, newCurrentPrice);
    }

    // ──────────────────────────────────────────────
    // STATUS UPDATE
    // ──────────────────────────────────────────────

    /**
     * Broadcast thay đổi trạng thái tới tất cả client liên quan.
     * Gọi bởi AuctionLifecycleService sau khi DB commit thành công.
     *
     * @param auctionId ID phiên
     * @param newStatus Trạng thái mới
     */
    public void broadcastStatusUpdate(String auctionId, AuctionStatus newStatus) {
        String message = buildJson("STATUS_UPDATE",
                "auctionId", auctionId,
                "status",    newStatus.name());

        sendSafely(auctionId, message);
        System.out.printf("[FrontendNotifier] STATUS_UPDATE → phiên=%s | status=%s%n",
                auctionId, newStatus);
    }

    /**
     * Broadcast kết thúc phiên kèm thông tin người thắng.
     * Gọi bởi AuctionLifecycleService.finishAuction() khi có bidder.
     *
     * @param auctionId  ID phiên
     * @param winnerId   ID người thắng
     * @param finalPrice Giá thắng cuối cùng
     */
    public void broadcastAuctionFinished(String auctionId, String winnerId, double finalPrice) {
        String message = buildJson("AUCTION_FINISHED",
                "auctionId",  auctionId,
                "winnerId",   winnerId,
                "finalPrice", String.valueOf(finalPrice));

        sendSafely(auctionId, message);
        System.out.printf("[FrontendNotifier] AUCTION_FINISHED → phiên=%s | winner=%s | giá=%.0f%n",
                auctionId, winnerId, finalPrice);
    }

    // ──────────────────────────────────────────────
    // WALLET UPDATE
    // ──────────────────────────────────────────────
    public void notifyBalanceUpdated(String userId, double newBalance) {
        String message = buildJson("BALANCE_UPDATED",
                "userId", userId,
                "balance", String.valueOf(newBalance));

        try {
            broadcaster.broadcastToUser(userId, message);
        } catch (Exception e) {
            System.err.printf("[FrontendNotifier] Gửi balance thất bại user=%s: %s%n",
                    userId, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    /**
     * Gọi broadcaster và bắt exception.
     * WebSocket fail KHÔNG được propagate lên Service — DB đã commit rồi,
     * business flow không được rollback chỉ vì socket lỗi.
     */
    private void sendSafely(String auctionId, String jsonMessage) {
        try {
            broadcaster.broadcastToRoom(auctionId, jsonMessage);
        } catch (Exception e) {
            System.err.printf("[FrontendNotifier] Gửi socket thất bại cho phiên=%s: %s%n",
                    auctionId, e.getMessage());
        }
    }

    /**
     * Tạo JSON string không cần thư viện ngoài.
     * Nếu project dùng Jackson/Gson thì thay bằng ObjectMapper.writeValueAsString().
     * Params: "key1", "val1", "key2", "val2", ...
     */
    private String buildJson(String type, String... kvPairs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type).append("\"");
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            sb.append(",\"").append(kvPairs[i]).append("\":\"")
                    .append(kvPairs[i + 1]).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}