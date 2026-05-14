package model.item;

public enum ItemType{
    ELECTRONICS, ART, VEHICLE;
    // Sau có thể thêm type mới

    // Để hiển thị trên dropdown JavaFX
    @Override
    public String toString() {
        return name().charAt(0) + name().substring(1).toLowerCase();
        // "Electronics", "Art", "Vehicle"
    }
}
