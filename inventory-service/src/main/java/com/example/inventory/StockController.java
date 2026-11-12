package com.example.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/** Stock reservations, called by order-service's checkout. */
@RestController
@RequestMapping("/api/stock")
@CrossOrigin(origins = "*")
public class StockController {

    private final StockService stock;

    public StockController(StockService stock) {
        this.stock = stock;
    }

    @PostMapping("/{id}/reserve")
    public Item reserve(@PathVariable Long id, @RequestParam int units, @RequestParam String checkout) {
        return stock.reserve(id, units, checkout);
    }

    @PostMapping("/{id}/release")
    public Item release(@PathVariable Long id, @RequestParam int units, @RequestParam String checkout) {
        return stock.release(id, units, checkout);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientStock(InsufficientStockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", HttpStatus.CONFLICT.value(),
            "error", "InsufficientStock",
            "message", e.getMessage(),
            "itemId", e.getItemId(),
            "requested", e.getRequested(),
            "available", e.getAvailable()
        ));
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(InventoryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", HttpStatus.NOT_FOUND.value(),
            "error", "InventoryNotFoundException",
            "message", e.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", HttpStatus.BAD_REQUEST.value(),
            "error", "BadRequest",
            "message", e.getMessage()
        ));
    }
}
