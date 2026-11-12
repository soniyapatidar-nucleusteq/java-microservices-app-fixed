package com.example.order.checkout;

public class InvalidCheckoutException extends RuntimeException {
    public InvalidCheckoutException(String message) {
        super(message);
    }
}
