package com.example.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*")
public class InventoryApplication {

    private static final Logger log = LoggerFactory.getLogger(InventoryApplication.class);

    private final ItemRepository repository;

    public InventoryApplication(ItemRepository repository) {
        this.repository = repository;
    }

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            if (repository.count() > 0) return;
            repository.saveAll(List.of(
                new Item(null, "Laptop", 10),
                new Item(null, "Smartphone", 25),
                new Item(null, "Wireless Headphones", 50),
                new Item(null, "Mechanical Keyboard", 30),
                new Item(null, "Wireless Mouse", 75),
                new Item(null, "27-inch Monitor", 15),
                new Item(null, "USB-C Hub", 60),
                new Item(null, "External SSD 1TB", 40),
                new Item(null, "Webcam 1080p", 35),
                new Item(null, "Laptop Stand", 45)
            ));
        };
    }

    @GetMapping
    public List<Item> getAll() {
        try {
            List<Item> items = repository.findAll();
            log.info("GET /api/items succeeded: returned {} items", items.size());
            return items;
        } catch (RuntimeException e) {
            log.error("GET /api/items failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping
    public Item create(@RequestBody Item item) {
        try {
            Item saved = repository.save(item);
            log.info("POST /api/items succeeded: created item id={} name='{}' quantity={}",
                saved.getId(), saved.getName(), saved.getQuantity());
            return saved;
        } catch (RuntimeException e) {
            log.error("POST /api/items failed for name='{}': {}", item.getName(), e.getMessage(), e);
            throw e;
        }
    }

    @PutMapping("/{id}")
    public Item update(@PathVariable Long id, @RequestBody Item details) {
        try {
            Item item = repository.findById(id).orElseThrow(() -> new InventoryNotFoundException(id));
            item.setName(details.getName());
            item.setQuantity(details.getQuantity());
            Item saved = repository.save(item);
            log.info("PUT /api/items/{} succeeded: name='{}' quantity={}", id, saved.getName(), saved.getQuantity());
            return saved;
        } catch (InventoryNotFoundException e) {
            throw e; // logged by handleInventoryNotFound
        } catch (RuntimeException e) {
            log.error("PUT /api/items/{} failed: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        try {
            repository.deleteById(id);
            log.info("DELETE /api/items/{} succeeded", id);
        } catch (RuntimeException e) {
            log.error("DELETE /api/items/{} failed: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    // Always throws InventoryNotFoundException, to exercise the 404 error path.
    @GetMapping("/errors/not-found")
    public Item throwInventoryNotFound(@RequestParam(defaultValue = "-1") Long id) {
        log.info("GET /api/items/errors/not-found called with id={}, throwing InventoryNotFoundException", id);
        throw new InventoryNotFoundException(id);
    }

    // Always throws OutOfMemoryError, to exercise the 500 error path.
    // The error is simulated: no memory is actually allocated, so the service stays up.
    @GetMapping("/errors/out-of-memory")
    public Item throwOutOfMemory() {
        log.info("GET /api/items/errors/out-of-memory called, throwing OutOfMemoryError");
        throw new OutOfMemoryError("Out Of Memory Exception: simulated inventory service memory exhaustion");
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleInventoryNotFound(InventoryNotFoundException e) {
        log.error("Returning 404: {}", e.getMessage());
        return errorResponse(HttpStatus.NOT_FOUND, "InventoryNotFoundException", e.getMessage());
    }

    @ExceptionHandler(OutOfMemoryError.class)
    public ResponseEntity<Map<String, Object>> handleOutOfMemory(OutOfMemoryError e) {
        log.error("Returning 500: {}", e.getMessage(), e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "OutOfMemoryError", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", status.value(),
            "error", error,
            "message", message
        ));
    }
}
