package com.example.order.checkout;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prices a basket. The rules, as the price list states them:
 *
 *   volume discount  a basket line of 5 to 9 units is 5% off, of 10 units or more 10% off
 *   coupon           WELCOME10 10% and LOYAL15 15% off what is left after the volume
 *                    discount; FREESHIP 5.00 off. Any other code is not accepted.
 *   tax              8% of the price after discounts
 *
 * Every amount is rounded to the cent.
 */
@Component
public class PricingEngine {

    static final double TAX_RATE = 0.08;
    static final Map<String, Double> PERCENT_OFF = Map.of("WELCOME10", 0.10, "LOYAL15", 0.15);
    static final Map<String, Double> AMOUNT_OFF = Map.of("FREESHIP", 5.00);
    /** Volume discount per tier of TIER_SIZE units: 0-4 units, 5-9, 10 or more (the last tier). */
    static final List<Double> VOLUME_TIERS = List.of(0.0, 0.05, 0.10);
    static final int TIER_SIZE = 5;

    /** One basket line with its unit price, as inventory-service sold it. */
    public record PricedLine(long itemId, String name, int quantity, double unitPrice) {
        double amount() { return quantity * unitPrice; }
    }

    public record Quote(double subtotal, double volumeDiscount, double couponDiscount, double tax, double total) {}

    public Quote quote(List<PricedLine> lines, String couponCode) {
        double subtotal = 0;
        int basketUnits = 0;
        for (PricedLine line : lines) {
            subtotal += line.amount();
            basketUnits += line.quantity();
        }
        double volumeRate = volumeRate(basketUnits);
        double volumeDiscount = 0;
        for (PricedLine line : lines) {
            volumeDiscount += line.amount() * volumeRate;
        }
        volumeDiscount = cents(volumeDiscount);
        double afterVolume = subtotal - volumeDiscount;
        double couponDiscount = cents(couponDiscount(couponCode, afterVolume));
        double taxable = afterVolume - couponDiscount;
        double tax = cents(taxable * TAX_RATE);
        return new Quote(cents(subtotal), volumeDiscount, couponDiscount, tax, cents(taxable + tax));
    }

    static double volumeRate(int units) {
        int tier = Math.min(units / TIER_SIZE, VOLUME_TIERS.size());
        return VOLUME_TIERS.get(tier);
    }

    double couponDiscount(String couponCode, double amount) {
        if (couponCode == null || couponCode.isBlank()) {
            return 0.0;
        }
        String code = couponCode.trim().toUpperCase(Locale.ROOT);
        if (AMOUNT_OFF.containsKey(code)) {
            return Math.min(AMOUNT_OFF.get(code), amount);
        }
        double rate = PERCENT_OFF.get(code);
        return amount * rate;
    }

    static double cents(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
