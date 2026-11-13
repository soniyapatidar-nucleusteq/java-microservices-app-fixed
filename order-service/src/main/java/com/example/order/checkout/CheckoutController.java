package com.example.order.checkout;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final CheckoutWorkflow workflow;
    private final CheckoutRepository checkouts;
    private final CustomerSummary summaries;

    public CheckoutController(CheckoutWorkflow workflow, CheckoutRepository checkouts, CustomerSummary summaries) {
        this.workflow = workflow;
        this.checkouts = checkouts;
        this.summaries = summaries;
    }

    @PostMapping
    public ResponseEntity<Checkout> checkout(@RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflow.run(request));
    }

    @GetMapping
    public List<Checkout> recent() {
        return workflow.recent();
    }

    @GetMapping("/summary")
    public CustomerSummary.Summary summary(@RequestParam String customer) {
        return summaries.of(customer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Checkout> one(@PathVariable Long id) {
        return checkouts.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(InvalidCheckoutException.class)
    public ResponseEntity<Map<String, Object>> invalid(InvalidCheckoutException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<Map<String, Object>> outOfStock(OutOfStockException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InventoryUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(InventoryUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Inventory is not reachable right now, please try again shortly");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> failed(RuntimeException e, HttpServletRequest request) {
        log.error("{} {} failed", request.getMethod(), request.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed");
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", status.value(),
            "message", message
        ));
    }
}
