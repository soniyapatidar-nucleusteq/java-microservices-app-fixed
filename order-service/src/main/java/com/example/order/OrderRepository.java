package com.example.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<ProductOrder, Long> {
    List<ProductOrder> findByCheckoutIdOrderById(Long checkoutId);
}
