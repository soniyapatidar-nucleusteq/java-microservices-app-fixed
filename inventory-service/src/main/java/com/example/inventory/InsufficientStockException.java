package com.example.inventory;

public class InsufficientStockException extends RuntimeException {

    private final long itemId;
    private final int requested;
    private final int available;

    public InsufficientStockException(long itemId, int requested, int available) {
        super("Insufficient stock for item " + itemId + ": requested " + requested + ", available " + available);
        this.itemId = itemId;
        this.requested = requested;
        this.available = available;
    }

    public long getItemId() { return itemId; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
