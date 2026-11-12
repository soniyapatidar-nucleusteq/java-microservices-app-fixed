package com.example.order;

import com.example.order.checkout.WorkflowTrace;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SpringBootApplication
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderApplication {

    private final OrderRepository repository;
    private final WorkflowTrace trace;

    public OrderApplication(OrderRepository repository, WorkflowTrace trace) {
        this.repository = repository;
        this.trace = trace;
    }

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }

    @Bean
    CommandLineRunner seedOrders() {
        return args -> {
            if (repository.count() > 0) return;
            repository.saveAll(List.of(
                new ProductOrder(null, "Laptop", 1200.00),
                new ProductOrder(null, "Wireless Headphones", 150.00),
                new ProductOrder(null, "Smartphone", 799.99),
                new ProductOrder(null, "Mechanical Keyboard", 89.50),
                new ProductOrder(null, "Wireless Mouse", 29.99),
                new ProductOrder(null, "27-inch Monitor", 249.00),
                new ProductOrder(null, "USB-C Hub", 45.00),
                new ProductOrder(null, "External SSD 1TB", 109.99),
                new ProductOrder(null, "Webcam 1080p", 59.90),
                new ProductOrder(null, "Laptop Stand", 34.75)
            ));
        };
    }

    @GetMapping
    public List<ProductOrder> getAll() { return repository.findAll(); }

    @PostMapping
    public ProductOrder create(@RequestBody ProductOrder order) {
        if (order.getQuantity() <= 0) order.setQuantity(1);
        ProductOrder saved = repository.save(order);
        trace.order("place-order", saved.getId(), saved.getQuantity(), "ok");
        return saved;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductOrder> getOne(@PathVariable Long id) {
        return repository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ProductOrder order = repository.findById(id).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();
        repository.delete(order);
        trace.order("cancel-order", id, order.getQuantity(), "ok");
        return ResponseEntity.noContent().build();
    }
}
