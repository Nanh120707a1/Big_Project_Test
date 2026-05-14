package exception.item;

/**
 * Ném khi không tìm thấy Item theo id trong Repository hoặc Warehouse.
 *
 * Dùng tại:
 *   - ItemService.findItemOrThrow()   → itemRepository.findById() trả về empty
 *   - AuctionService.createAuction()  → items.containsKey(itemId) == false
 *
 * Ví dụ sử dụng:
 *   throw new ItemNotFoundException(itemId);
 */
public class ItemNotFoundException extends ItemException {

    private static final String CODE = "ITEM_NOT_FOUND";

    public ItemNotFoundException(String itemId) {
        super(CODE, "Không tìm thấy sản phẩm với id: " + itemId);
    }
}
