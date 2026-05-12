package model.root;


/**
 * Lớp cha chung cho tất cả entity trong hệ thống.
 * Cung cấp ID tự sinh và timestamps (createdAt, updatedAt).
 */

import java.time.LocalDateTime;
import java.util.UUID; // dùng để tạo ID duy nhất

public abstract class BaseEntity{
    private final String id;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected BaseEntity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** Constructor dùng khi load lại từ DB (id đã có sẵn) */
    protected BaseEntity(String id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id        = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ------------ Getters ------------
    public String getId() {return id;}
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // -----------Utility ------------
    /**
     * Gọi một lần duy nhất trong Service, ngay trước khi persist xuống DB.
     */
    protected void markUpdated() {
        this.updatedAt = LocalDateTime.now();
    }
}


