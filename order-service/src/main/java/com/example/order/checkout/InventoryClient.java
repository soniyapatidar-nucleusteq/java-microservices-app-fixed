package com.example.order.checkout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

/**
 * inventory-service's stock API. A call that cannot reach the service (refused,
 * unknown host, timed out) is retried with a growing pause; one the service answers
 * is not: a 409 means the stock is not there, a 404 that the item does not exist.
 */
@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);
    static final int ATTEMPTS = 3;
    private static final long PAUSE_MS = 250;

    private final RestClient http;

    public InventoryClient(@Value("${inventory.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(1000);
        timeouts.setReadTimeout(3000);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts).build();
    }

    /** An inventory item as inventory-service returns it. */
    public record StockItem(Long id, String name, int quantity, double unitPrice) {}

    public StockItem reserve(long itemId, int units, long checkout) {
        return call("reserve", itemId, () -> http.post()
            .uri("/api/stock/{id}/reserve?units={units}&checkout={checkout}", itemId, units, checkout)
            .retrieve()
            .onStatus(status -> status.value() == 409, (request, response) -> {
                throw new OutOfStockException(itemId, units);
            })
            .onStatus(status -> status.value() == 404, (request, response) -> {
                throw new InvalidCheckoutException("There is no inventory item " + itemId);
            })
            .body(StockItem.class));
    }

    public StockItem release(long itemId, int units, long checkout) {
        return call("release", itemId, () -> http.post()
            .uri("/api/stock/{id}/release?units={units}&checkout={checkout}", itemId, units, checkout)
            .retrieve()
            .body(StockItem.class));
    }

    private <T> T call(String action, long itemId, Supplier<T> request) {
        ResourceAccessException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return request.get();
            } catch (ResourceAccessException e) {
                last = e;
                log.warn("Inventory {} of item {} did not go through (attempt {}/{}): {}",
                    action, itemId, attempt, ATTEMPTS, e.getMessage());
                pause(PAUSE_MS * attempt);
            }
        }
        throw new InventoryUnavailableException("Retries exhausted after " + ATTEMPTS
            + " attempts calling inventory-service to " + action + " item " + itemId, last);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
