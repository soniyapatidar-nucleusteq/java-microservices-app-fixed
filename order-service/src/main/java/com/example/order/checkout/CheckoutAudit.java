package com.example.order.checkout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** The checkout audit trail (`order-audit`): what was sold, to whom, and for how much. */
@Component
public class CheckoutAudit {

    private static final Logger audit = LoggerFactory.getLogger("order-audit");

    public void confirmed(Checkout checkout) {
        audit.info("checkout {} confirmed for customer {}: {} units, charged {} USD",
            checkout.getId(), checkout.getCustomer(), checkout.getUnits(), money(checkout.getTotal()));
    }

    public void cancelled(Checkout checkout, String reason) {
        audit.warn("checkout {} cancelled for customer {}: {}", checkout.getId(), checkout.getCustomer(), reason);
    }

    public void invoiceMismatch(Checkout checkout, double invoice) {
        audit.warn("checkout {} invoice check failed: charged {} USD, invoice says {} USD",
            checkout.getId(), money(checkout.getTotal()), money(invoice));
    }

    private static String money(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
