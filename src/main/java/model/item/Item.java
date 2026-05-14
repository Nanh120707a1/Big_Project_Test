package model.item;


import model.root.BaseEntity;

import java.time.LocalDateTime;

/**
 * Sản phẩm đấu giá — không còn abstract, gộp Electronics/Art/Vehicle vào đây.
 * Phân loại bằng field itemType (enum ItemType).
 */
public class Item extends BaseEntity {

    private String     name;
    private String     description;
    private double     originalPrice;
    private String     imageUrl;       // đường dẫn ảnh trên server, null nếu chưa upload
    private ItemType   itemType;       // ELECTRONICS | ART | VEHICLE
    private final String ownerId;     // không đổi sau khi tạo


    // ──────────────────────────────────────────────
    // CONSTRUCTOR 1 — tạo mới (ItemFactory gọi)
    // ──────────────────────────────────────────────
    public Item(String name, String description, double originalPrice,
                String ownerId, ItemType itemType, String imageUrl){
        super();
        this.name         = name;
        this.description  = description;
        this.originalPrice = originalPrice;
        this.ownerId      = ownerId;
        this.itemType     = itemType;
        this.imageUrl     = imageUrl;
    }

    // ──────────────────────────────────────────────
    // CONSTRUCTOR 2 — load từ DB (MySQLItemRepository gọi)
    // ──────────────────────────────────────────────
    public Item(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                String name, String description, double originalPrice,
                String ownerId, ItemType itemType, String imageUrl) {
        super(id, createdAt, updatedAt);
        this.name         = name;
        this.description  = description;
        this.originalPrice = originalPrice;
        this.ownerId      = ownerId;
        this.itemType     = itemType;
        this.imageUrl     = imageUrl;
    }

    // ──────────────────────────────────────────────
    // GETTERS & SETTERS
    // ──────────────────────────────────────────────
    public String     getItemName()             { return name; }
    public String     getItemDescription()      { return description; }
    public double     getOriginalPrice()    { return originalPrice; }
    public String     getImageUrl()         { return imageUrl; }
    public ItemType   getItemType()         { return itemType; }
    public String     getOwnerId()          { return ownerId; }

    // ──────────────────────────────────────────────
    // UTILITY
    // ──────────────────────────────────────────────
    @Override
    public String toString() {
        return String.format("Item[%s | %s | %s | %.2f]",
                getId(), itemType, name, originalPrice);
    }
}