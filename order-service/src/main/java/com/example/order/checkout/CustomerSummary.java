package com.example.order.checkout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What a customer has bought through checkout: how many checkouts, how many went
 * through, and their average basket (units and amount per confirmed checkout).
 */
@Service
public class CustomerSummary {

    private static final Logger log = LoggerFactory.getLogger(CustomerSummary.class);

    private final CheckoutRepository checkouts;

    public CustomerSummary(CheckoutRepository checkouts) {
        this.checkouts = checkouts;
    }

    public record Summary(String customer, int checkouts, int confirmed, int units, double spent,
                          int averageUnits, double averageBasket) {}

    public Summary of(String customer) {
        String who = customer == null ? "" : customer.trim();
        List<Checkout> all = checkouts.findByCustomerIgnoreCase(who);
        int confirmed = 0;
        int units = 0;
        double spent = 0;
        for (Checkout checkout : all) {
            if (checkout.getStatus() == Checkout.Status.CONFIRMED) {
                confirmed++;
                units += checkout.getUnits();
                spent += checkout.getTotal();
            }
        }
        int averageUnits = units / confirmed;
        double averageBasket = PricingEngine.cents(spent / confirmed);
        log.info("Summary for {}: {} checkouts, {} confirmed, {} spent", who, all.size(), confirmed,
            PricingEngine.cents(spent));
        return new Summary(who, all.size(), confirmed, units, PricingEngine.cents(spent), averageUnits, averageBasket);
    }
}
