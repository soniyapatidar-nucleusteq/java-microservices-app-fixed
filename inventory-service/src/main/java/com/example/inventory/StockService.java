package com.example.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds stock for a checkout: a reservation takes units off the shelf, a release puts
 * them back (a cancelled or failed checkout). Each change is one transaction, so two
 * checkouts reserving the last units cannot both succeed, and each is written to the
 * stock ledger (StockMovement).
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    static final int REORDER_AT = 5;

    private final ItemRepository repository;
    private final StockMovementRepository ledger;

    public StockService(ItemRepository repository, StockMovementRepository ledger) {
        this.repository = repository;
        this.ledger = ledger;
    }

    @Transactional
    public Item reserve(long itemId, int units, String checkout) {
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive, got " + units);
        }
        Item item = repository.findById(itemId).orElseThrow(() -> new InventoryNotFoundException(itemId));
        if (item.getQuantity() < units) {
            log.warn("Checkout {} asked for {} x '{}' but only {} are in stock", checkout, units, item.getName(),
                item.getQuantity());
            throw new InsufficientStockException(itemId, units, item.getQuantity());
        }
        item.setQuantity(item.getQuantity() - units);
        Item saved = repository.save(item);
        ledger.save(new StockMovement(itemId, -units, "reserved for checkout " + checkout));
        log.info("Reserved {} x '{}' for checkout {}, {} left", units, item.getName(), checkout, saved.getQuantity());
        if (saved.getQuantity() < REORDER_AT) {
            lowStock(saved);
        }
        return saved;
    }

    @Transactional
    public Item release(long itemId, int units, String checkout) {
        Item item = repository.findById(itemId).orElseThrow(() -> new InventoryNotFoundException(itemId));
        item.setQuantity(item.getQuantity() + units);
        Item saved = repository.save(item);
        ledger.save(new StockMovement(itemId, units, "released from checkout " + checkout));
        log.info("Released {} x '{}' from checkout {}, {} in stock", units, item.getName(), checkout, saved.getQuantity());
        return saved;
    }

    /** The buyers' reorder list: they read it off the console. */
    private static void lowStock(Item item) {
        System.out.println("low stock: " + item.getName() + " has " + item.getQuantity()
            + " left (reorder at " + REORDER_AT + ")");
    }
}
