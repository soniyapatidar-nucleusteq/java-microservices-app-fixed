package com.example.order.checkout;

import java.util.List;

/** What the customer asks for: who they are, an optional coupon, and the basket. */
public record CheckoutRequest(String customer, String couponCode, List<Line> lines) {

    /** One basket line: an inventory item and how many units of it. */
    public record Line(Long itemId, int quantity) {}
}
