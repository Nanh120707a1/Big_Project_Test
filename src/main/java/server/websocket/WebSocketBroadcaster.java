package server.websocket;
/**
 * WebSocketBroadcaster — interface trừu tượng hoá WebSocket library cụ thể.
 *
 * Vấn đề cũ: FrontendNotifier phụ thuộc trực tiếp vào WebSocketServer (class cụ thể),
 * khiến code bị gắn chặt với thư viện WebSocket đang dùng.
 *
 * Giải pháp: FrontendNotifier chỉ phụ thuộc vào interface này.
 * Team chỉ cần implement một adapter class cho thư viện thực tế của project
 * (Jetty, Java-WebSocket, Netty, Socket.IO Java, v.v.).
 *
 * Lợi ích:
 *  - Đổi thư viện WebSocket không cần sửa FrontendNotifier hay Service.
 *  - Dễ mock trong unit test: new FrontendNotifier(mockBroadcaster).
 *  - Tách rõ: FrontendNotifier biết gửi cái gì, Broadcaster biết gửi như thế nào.
 */
public interface WebSocketBroadcaster {

    /**
     * Gửi message JSON tới tất cả client đang xem phiên đấu giá đó.
     * "Room" / "channel" tương ứng với auctionId.
     *
     * Implementation phải đảm bảo:
     *  - Thread-safe (nhiều service có thể gọi đồng thời).
     *  - Non-blocking hoặc nhanh — không gây delay cho business flow.
     *  - Fail silently — lỗi socket không được làm crash caller.
     *
     * @param auctionId  Room ID = auction ID
     * @param jsonMessage Message JSON đã build sẵn
     */
    void broadcastToRoom(String auctionId, String jsonMessage);
}