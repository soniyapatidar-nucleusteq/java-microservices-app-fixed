package com.example.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds stock for a checkout: a reservation takes units off the shelf, a release puts
 * them back (a cancelled or failed checkout). Each change is one transaction, so two
 * checkouts reserving the last units cannot both succeed.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final ItemRepository repository;

    public StockService(ItemRepository repository) {
        this.repository = repository;
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
        log.info("Reserved {} x '{}' for checkout {}, {} left", units, item.getName(), checkout, saved.getQuantity());
        return saved;
    }

    @Transactional
    public Item release(long itemId, int units, String checkout) {
        Item item = repository.findById(itemId).orElseThrow(() -> new InventoryNotFoundException(itemId));
        item.setQuantity(item.getQuantity() + units);
        Item saved = repository.save(item);
        log.info("Released {} x '{}' from checkout {}, {} in stock", units, item.getName(), checkout, saved.getQuantity());
        return saved;
    }
}
