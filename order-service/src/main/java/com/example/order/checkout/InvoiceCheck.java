package com.example.order.checkout;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Finance's check on every confirmed checkout: the invoice total, worked out again
 * from the price list line by line, must equal what the customer was charged.
 */
@Component
public class InvoiceCheck {

    public double invoiceTotal(List<PricingEngine.PricedLine> lines, String couponCode) {
        double net = 0;
        for (PricingEngine.PricedLine line : lines) {
            double amount = line.quantity() * line.unitPrice();
            net += amount - amount * PricingEngine.volumeRate(line.quantity());
        }
        net = PricingEngine.cents(net);
        String code = couponCode == null ? "" : couponCode.trim().toUpperCase(Locale.ROOT);
        double coupon = PricingEngine.AMOUNT_OFF.containsKey(code) ? Math.min(PricingEngine.AMOUNT_OFF.get(code), net)
            : net * PricingEngine.PERCENT_OFF.getOrDefault(code, 0.0);
        double taxable = net - PricingEngine.cents(coupon);
        return PricingEngine.cents(taxable + PricingEngine.cents(taxable * PricingEngine.TAX_RATE));
    }

    public boolean matches(double charged, double invoice) {
        return Math.abs(charged - invoice) < 0.005;
    }
}
