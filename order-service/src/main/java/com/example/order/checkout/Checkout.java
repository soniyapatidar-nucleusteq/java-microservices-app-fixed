package com.example.order.checkout;

import jakarta.persistence.*;

import java.time.Instant;

/** One customer checkout: a basket of items bought together, and how it ended. */
@Entity
@Table(name = "checkouts")
public class Checkout {

    public enum Status { PENDING, CONFIRMED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customer;
    private String couponCode;
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;
    private int units;
    private double subtotal;
    private double volumeDiscount;
    private double couponDiscount;
    private double tax;
    private double total;
    private String failureReason;
    private Instant createdAt = Instant.now();

    protected Checkout() {}

    public Checkout(String customer, String couponCode, int units) {
        this.customer = customer;
        this.couponCode = couponCode;
        this.units = units;
    }

    public void confirm(PricingEngine.Quote quote) {
        this.subtotal = quote.subtotal();
        this.volumeDiscount = quote.volumeDiscount();
        this.couponDiscount = quote.couponDiscount();
        this.tax = quote.tax();
        this.total = quote.total();
        this.status = Status.CONFIRMED;
    }

    public void fail(String reason) {
        this.status = Status.FAILED;
        this.failureReason = reason;
    }

    public Long getId() { return id; }
    public String getCustomer() { return customer; }
    public String getCouponCode() { return couponCode; }
    public Status getStatus() { return status; }
    public int getUnits() { return units; }
    public double getSubtotal() { return subtotal; }
    public double getVolumeDiscount() { return volumeDiscount; }
    public double getCouponDiscount() { return couponDiscount; }
    public double getTax() { return tax; }
    public double getTotal() { return total; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
