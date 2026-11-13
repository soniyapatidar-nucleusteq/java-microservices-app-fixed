package com.example.order.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckoutRepository extends JpaRepository<Checkout, Long> {
    List<Checkout> findTop20ByOrderByIdDesc();

    List<Checkout> findByCustomerIgnoreCase(String customer);
}
