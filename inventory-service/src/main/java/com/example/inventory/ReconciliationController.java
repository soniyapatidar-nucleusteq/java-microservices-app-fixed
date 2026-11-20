package com.example.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/** Stock reconciliation runs: start one, follow it, cancel it. */
@RestController
@RequestMapping("/api/reconciliation")
@CrossOrigin(origins = "*")
public class ReconciliationController {

    private final StockReconciliation reconciliation;

    public ReconciliationController(StockReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping
    public StockReconciliation.Status status() {
        return reconciliation.status();
    }

    @PostMapping("/start")
    public ResponseEntity<StockReconciliation.Status> start() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reconciliation.start());
    }

    @PostMapping("/cancel")
    public StockReconciliation.Status cancel() {
        return reconciliation.cancel();
    }

    @ExceptionHandler({StockReconciliation.AlreadyRunningException.class, StockReconciliation.NotRunningException.class})
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", HttpStatus.CONFLICT.value(),
            "message", e.getMessage()
        ));
    }
}
