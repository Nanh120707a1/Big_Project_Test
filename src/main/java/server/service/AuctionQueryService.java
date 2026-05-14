package server.service;
import server.dao.AuctionRepository;
import server.dto.AuctionCardDTO;

import java.util.List;

/**
 * AuctionQueryService — cung cấp các query không liên quan đến lifecycle.
 *
 * Tách riêng khỏi AuctionLifeCycleService để giữ Single Responsibility:
 *  - AuctionLifeCycleService  : quản lý trạng thái (create, approve, start, finish, cancel)
 *  - AuctionQueryService      : truy vấn dữ liệu (getMainViewAuctions, v.v.)
 *
 * MainView inject AuctionQueryService, không inject AuctionLifeCycleService.
 */
public class AuctionQueryService {

    private final AuctionRepository auctionRepository;

    public AuctionQueryService(AuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
    }

    // ──────────────────────────────────────────────
    // MAIN VIEW
    // ──────────────────────────────────────────────

    /**
     * Trả về danh sách phiên đấu giá dùng cho MainView.
     *
     * Dữ liệu JOIN auctions + items — MainView KHÔNG dùng Auction entity trực tiếp.
     * Chỉ lấy phiên active (status NOT IN PAID, CANCELED).
     *
     * @return List<AuctionCardDTO> sắp xếp theo start_time ASC
     */
    public List<AuctionCardDTO> getMainViewAuctions() {
        return auctionRepository.findMainViewAuctions();
    }
}
