package com.example.order.checkout;

public class OutOfStockException extends RuntimeException {
    public OutOfStockException(long itemId, int units) {
        super("Item " + itemId + " does not have " + units + " units in stock");
    }
}
