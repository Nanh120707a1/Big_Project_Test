package model.Auction;
/**
 * Lý do huỷ phiên đấu giá.
 * Hiển thị trên UI để Seller / Admin / Buyer biết tại sao phiên bị huỷ.
 */
public enum CancelReason {

    /** Phiên kết thúc mà không có ai đặt giá. Hệ thống tự huỷ. */
    NO_BIDDER,

    /** Người thắng nhấn "Từ chối thanh toán". Auction chuyển CANCELED. */
    BUYER_REJECTED,

    /** Admin từ chối duyệt phiên (item không hợp lệ, v.v.). */
    ADMIN_REJECTED
}