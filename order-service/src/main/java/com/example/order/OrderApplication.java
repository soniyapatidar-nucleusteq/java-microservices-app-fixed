package com.example.order;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SpringBootApplication
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderApplication {

    private final OrderRepository repository;

    public OrderApplication(OrderRepository repository) {
        this.repository = repository;
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
    public ProductOrder create(@RequestBody ProductOrder order) { return repository.save(order); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { repository.deleteById(id); }
}
