package server.service;

import model.Auction.Auction;
import model.Auction.AuctionStatus;
import model.Auction.CancelReason;
import model.factory.ItemFactory;
import model.item.Item;
import model.item.ItemType;
import model.user.Account;
import server.config.DatabaseConfig;
import server.dao.AuctionRepository;
import server.dao.ItemRepository;
import server.websocket.FrontendNotifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * AuctionLifecycleService — quản lý vòng đời phiên đấu giá.
 *
 * Trách nhiệm:
 *  - createAuction()        : Seller tạo phiên → tạo Item + Auction trong 1 transaction
 *  - approveAuction()       : Admin duyệt    → PENDING → OPEN, schedule start/end
 *  - rejectAuction()        : Admin từ chối  → PENDING → CANCELED (ADMIN_REJECTED)
 *  - startAuction()         : Scheduler gọi  → OPEN → RUNNING
 *  - finishAuction()        : Scheduler gọi  → RUNNING → FINISHED hoặc CANCELED (NO_BIDDER)
 *  - cancelAfterRejection() : Buyer reject   → FINISHED → CANCELED (BUYER_REJECTED)
 *
 * Bảo mật:
 *  - sellerId KHÔNG được lấy từ client request.
 *  - Mọi action có token đều dùng SessionManager.requireAccount(token) để resolve user.
 */
public class AuctionLifeCycleService {

    private final AuctionRepository      auctionRepository;
    private final ItemRepository         itemRepository;
    private final FrontendNotifier       frontendNotifier;
    private final AuctionSchedulerService schedulerService;
    private final SessionManager         sessionManager;

    public AuctionLifeCycleService(AuctionRepository auctionRepository,
                                   ItemRepository itemRepository,
                                   FrontendNotifier frontendNotifier,
                                   AuctionSchedulerService schedulerService,
                                   SessionManager sessionManager) {
        this.auctionRepository = auctionRepository;
        this.itemRepository    = itemRepository;
        this.frontendNotifier  = frontendNotifier;
        this.schedulerService  = schedulerService;
        this.sessionManager    = sessionManager;
    }

    // ──────────────────────────────────────────────
    // CREATE — token-based, transaction-safe
    // ──────────────────────────────────────────────

    /**
     * Seller tạo phiên đấu giá.
     *
     * Flow:
     *  1. Resolve sellerId từ token (không trust client).
     *  2. Tạo Item qua ItemFactory (validate input).
     *  3. Tạo Auction với itemId + sellerId từ bước 1-2.
     *  4. Lưu Item + Auction trong cùng 1 JDBC transaction.
     *     → Nếu bất kỳ bước nào lỗi: rollback toàn bộ, không có orphan data.
     *
     * @param token         Session token của Seller
     * @param itemType      Loại sản phẩm (ELECTRONICS | ART | VEHICLE)
     * @param itemName      Tên sản phẩm
     * @param description   Mô tả sản phẩm
     * @param imageUrl      Đường dẫn ảnh đã upload lên server
     * @param originalPrice Giá gốc sản phẩm (>= 0)
     * @param startingPrice Giá khởi điểm đấu giá (> 0)
     * @param startTime     Thời gian bắt đầu phiên
     * @param endTime       Thời gian kết thúc phiên (phải sau startTime)
     * @return Auction vừa tạo (status PENDING), hoặc null nếu lỗi hệ thống
     * @throws IllegalArgumentException nếu dữ liệu đầu vào không hợp lệ
     * @throws exception.auth.SessionNotFoundException nếu token không hợp lệ
     */
    public Auction createAuction(String token,
                                 ItemType itemType,
                                 String itemName,
                                 String description,
                                 String imageUrl,
                                 double originalPrice,
                                 double startingPrice,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime) {

        // ── 1. Resolve seller từ token — KHÔNG trust client ──
        Account seller = sessionManager.requireAccount(token);
        String sellerId = seller.getId();

        // ── 2. Validate thời gian ──
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        if (startingPrice <= 0) {
            throw new IllegalArgumentException("Giá khởi điểm phải > 0.");
        }

        // ── 3. Tạo Item qua Factory (validate + build object) ──
        // ItemFactory.createByItemType() ném IllegalArgumentException nếu input lỗi
        Item item = ItemFactory.createByItemType(
                itemType, itemName, description, originalPrice, sellerId, imageUrl);

        // ── 4. Tạo Auction — sellerId lấy từ token, không từ client ──
        Auction auction = new Auction(item.getId(), sellerId, startingPrice, startTime, endTime);

        // ── 5. Lưu Item + Auction trong cùng 1 transaction ──
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean itemSaved    = itemRepository.save(conn, item);
                boolean auctionSaved = auctionRepository.save(conn, auction);

                if (!itemSaved || !auctionSaved) {
                    conn.rollback();
                    System.err.printf("[LifecycleService] Transaction rollback: "
                            + "itemSaved=%b, auctionSaved=%b%n", itemSaved, auctionSaved);
                    return null;
                }

                conn.commit();
                System.out.printf("[LifecycleService] Tạo phiên thành công: "
                                + "auction=%s item=%s seller=%s (PENDING)%n",
                        auction.getId(), item.getId(), sellerId);
                return auction;

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[LifecycleService] Rollback createAuction: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        } catch (SQLException e) {
            System.err.println("[LifecycleService] Lỗi kết nối DB khi createAuction: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ──────────────────────────────────────────────
    // APPROVE / REJECT (Admin)
    // ──────────────────────────────────────────────

    /**
     * Admin duyệt phiên: PENDING → OPEN.
     * Sau khi approve mới schedule start/end.
     *
     * @param auctionId ID phiên
     * @param token     Session token của Admin
     */
    public boolean approveAuction(String auctionId, String token) {
        sessionManager.requireAdmin(token); // xác thực Admin
        Auction auction = requireAuction(auctionId);

        if (!auction.isPending()) {
            System.err.printf("[LifecycleService] Approve thất bại: phiên=%s không ở PENDING (hiện: %s)%n",
                    auctionId, auction.getStatus());
            return false;
        }

        auction.markAsOpen();
        boolean updated = auctionRepository.updateStatus(auctionId, AuctionStatus.OPEN);
        if (!updated) return false;

        System.out.printf("[LifecycleService] Phiên %s được duyệt → OPEN%n", auctionId);

        // Schedule start/end CHỈ sau khi approve thành công
        schedulerService.scheduleAuction(auctionId, auction.getStartTime(), auction.getEndTime());
        frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.OPEN);
        return true;
    }

    /**
     * Admin từ chối phiên: PENDING → CANCELED (ADMIN_REJECTED).
     *
     * @param auctionId ID phiên
     * @param token     Session token của Admin
     */
    public boolean rejectAuction(String auctionId, String token) {
        String adminId = sessionManager.requireAdmin(token).getId();
        Auction auction = requireAuction(auctionId);

        if (!auction.isPending()) {
            System.err.printf("[LifecycleService] Reject thất bại: phiên=%s không ở PENDING%n", auctionId);
            return false;
        }

        auction.markAsCanceled(CancelReason.ADMIN_REJECTED, adminId);
        boolean updated = auctionRepository.updateCancelInfo(
                auctionId, AuctionStatus.CANCELED, CancelReason.ADMIN_REJECTED, adminId);

        if (updated) {
            System.out.printf("[LifecycleService] Phiên %s bị từ chối bởi Admin%n", auctionId);
            frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.CANCELED);
        }
        return updated;
    }

    // ──────────────────────────────────────────────
    // START / FINISH (Scheduler gọi — không cần token)
    // ──────────────────────────────────────────────

    /**
     * Bắt đầu phiên: OPEN → RUNNING.
     * Gọi bởi AuctionSchedulerService khi đến startTime.
     * Không cần token — đây là system action, không phải user action.
     */
    public void startAuction(String auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            System.err.println("[LifecycleService] startAuction: phiên không tồn tại: " + auctionId);
            return;
        }
        if (!auction.isOpen()) {
            System.err.printf("[LifecycleService] startAuction: phiên=%s không ở OPEN (hiện: %s)%n",
                    auctionId, auction.getStatus());
            return;
        }

        auction.markAsRunning();
        boolean updated = auctionRepository.updateStatus(auctionId, AuctionStatus.RUNNING);
        if (updated) {
            System.out.printf("[LifecycleService] Phiên %s bắt đầu → RUNNING%n", auctionId);
            frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.RUNNING);
        }
    }

    /**
     * Kết thúc phiên: RUNNING → FINISHED hoặc CANCELED.
     * Gọi bởi AuctionSchedulerService khi đến endTime.
     * Không cần token — đây là system action.
     */
    public void finishAuction(String auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            System.err.println("[LifecycleService] finishAuction: phiên không tồn tại: " + auctionId);
            return;
        }
        if (!auction.isRunning()) {
            System.err.printf("[LifecycleService] finishAuction: phiên=%s không ở RUNNING (hiện: %s)%n",
                    auctionId, auction.getStatus());
            return;
        }

        if (!auction.hasBids()) {
            // Không có ai đặt giá → huỷ
            auction.markAsCanceled(CancelReason.NO_BIDDER, "SYSTEM");
            auctionRepository.updateCancelInfo(
                    auctionId, AuctionStatus.CANCELED, CancelReason.NO_BIDDER, "SYSTEM");

            System.out.printf("[LifecycleService] Phiên %s kết thúc không có bidder → CANCELED%n",
                    auctionId);
            frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.CANCELED);

        } else {
            // Có người thắng → FINISHED
            auction.markAsFinished();
            auctionRepository.updateStatus(auctionId, AuctionStatus.FINISHED);

            System.out.printf("[LifecycleService] Phiên %s kết thúc → FINISHED | "
                            + "Winner: %s | Giá: %.0f%n",
                    auctionId, auction.getLeadingBidderId(), auction.getCurrentPrice());

            frontendNotifier.broadcastAuctionFinished(
                    auctionId, auction.getLeadingBidderId(), auction.getCurrentPrice());
        }
    }

    // ──────────────────────────────────────────────
    // CANCEL (Buyer reject payment — gọi qua PaymentService)
    // ──────────────────────────────────────────────

    /**
     * Buyer từ chối thanh toán: FINISHED → CANCELED (BUYER_REJECTED).
     * Gọi bởi PaymentService.rejectPayment() — không gọi trực tiếp từ Controller.
     * buyerId đã được PaymentService resolve từ token trước khi gọi vào đây.
     *
     * @param auctionId ID phiên
     * @param buyerId   ID người thắng (đã verify bởi PaymentService)
     */
    public boolean cancelAfterRejection(String auctionId, String buyerId) {
        Auction auction = requireAuction(auctionId);

        if (!auction.isFinished()) {
            System.err.printf("[LifecycleService] cancelAfterRejection: phiên=%s không ở FINISHED%n",
                    auctionId);
            return false;
        }

        auction.markAsCanceled(CancelReason.BUYER_REJECTED, buyerId);
        boolean updated = auctionRepository.updateCancelInfo(
                auctionId, AuctionStatus.CANCELED, CancelReason.BUYER_REJECTED, buyerId);

        if (updated) {
            System.out.printf("[LifecycleService] Phiên %s bị huỷ do Buyer %s từ chối%n",
                    auctionId, buyerId);
            frontendNotifier.broadcastStatusUpdate(auctionId, AuctionStatus.CANCELED);
        }
        return updated;
    }

    // ──────────────────────────────────────────────
    // HELPER
    // ──────────────────────────────────────────────

    private Auction requireAuction(String auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Phiên đấu giá không tồn tại: " + auctionId));
    }
}