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

    /**
     * Gửi message realtime tới một user cụ thể thông qua WebSocket.
     *
     * Mục đích:
     *  - Dùng cho các event mang tính cá nhân (ví dụ: cập nhật số dư ví, thông báo thanh toán, thông báo riêng).
     *  - Khác với broadcastToRoom (gửi cho nhiều client cùng xem 1 auction),
     *    method này chỉ gửi tới các session (connection) thuộc về một userId duy nhất.
     *
     * Cách hoạt động (tuỳ implement):
     *  - Server cần maintain mapping: userId → list WebSocket sessions.
     *  - Khi gọi method này, message sẽ được gửi tới tất cả session đang active của user đó
     *    (ví dụ: user mở nhiều tab hoặc nhiều thiết bị).
     *
     * Đảm bảo:
     *  - Không throw exception ra ngoài (nên handle nội bộ).
     *  - Nếu user offline (không có session nào) → bỏ qua, không lỗi.
     *
     * Lưu ý:
     *  - Method này nên được gọi SAU KHI transaction DB đã commit thành công.
     *  - Không dùng trong transaction để tránh ảnh hưởng business logic nếu socket fail.
     *
     * @param userId  ID của user nhận message
     * @param message Nội dung message (thường là JSON string)
     */
    void broadcastToUser(String userId, String message);
}