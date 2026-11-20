package com.example.inventory;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One change to an item's stock: the ledger. Opening stock, a reservation (negative),
 * a release, a supplier delivery. An item's shelf count should always equal the sum of
 * its movements; StockReconciliation checks that it does.
 */
@Entity
public class StockMovement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long itemId;
    private int delta;
    private String reason;
    private Instant at = Instant.now();

    protected StockMovement() {}

    public StockMovement(Long itemId, int delta, String reason) {
        this.itemId = itemId;
        this.delta = delta;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getItemId() { return itemId; }
    public int getDelta() { return delta; }
    public String getReason() { return reason; }
    public Instant getAt() { return at; }
}
