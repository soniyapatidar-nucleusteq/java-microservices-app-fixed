package com.example.order.checkout;

import com.example.order.OrderRepository;
import com.example.order.ProductOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * A checkout, step by step:
 *
 *   reserve-stock  each basket line's units are reserved in inventory-service
 *   price          the basket is priced (volume discount, coupon, tax)
 *   record-orders  one order per basket line
 *   confirm        the checkout is confirmed and goes on the audit trail
 *
 * When a step fails, the stock already reserved is released and the checkout is
 * marked failed.
 */
@Service
public class CheckoutWorkflow {

    private static final Logger log = LoggerFactory.getLogger(CheckoutWorkflow.class);
    static final int MAX_LINES = 20;

    private final CheckoutRepository checkouts;
    private final OrderRepository orders;
    private final InventoryClient inventory;
    private final PricingEngine pricing;
    private final InvoiceCheck invoiceCheck;
    private final WorkflowTrace trace;
    private final CheckoutAudit audit;

    public CheckoutWorkflow(CheckoutRepository checkouts, OrderRepository orders, InventoryClient inventory,
                            PricingEngine pricing, InvoiceCheck invoiceCheck, WorkflowTrace trace, CheckoutAudit audit) {
        this.checkouts = checkouts;
        this.orders = orders;
        this.inventory = inventory;
        this.pricing = pricing;
        this.invoiceCheck = invoiceCheck;
        this.trace = trace;
        this.audit = audit;
    }

    public Checkout run(CheckoutRequest request) {
        validate(request);
        int units = request.lines().stream().mapToInt(CheckoutRequest.Line::quantity).sum();
        Checkout checkout = checkouts.save(new Checkout(request.customer().trim(), request.couponCode(), units));
        long id = checkout.getId();
        log.info("Checkout {} started for {}: {} lines, {} units", id, checkout.getCustomer(), request.lines().size(), units);

        String step = "reserve-stock";
        List<PricingEngine.PricedLine> held = new ArrayList<>();
        try {
            for (CheckoutRequest.Line line : request.lines()) {
                InventoryClient.StockItem item = inventory.reserve(line.itemId(), line.quantity(), id);
                held.add(new PricingEngine.PricedLine(item.id(), item.name(), line.quantity(), item.unitPrice()));
            }
            trace.step(step, id, "ok");

            step = "price";
            PricingEngine.Quote quote = pricing.quote(held, request.couponCode());
            trace.step(step, id, "ok");

            step = "record-orders";
            for (PricingEngine.PricedLine line : held) {
                orders.save(new ProductOrder(line.name(), PricingEngine.cents(line.amount()), line.quantity(), id));
            }
            trace.step(step, id, "ok");

            step = "confirm";
            checkout.confirm(quote);
            checkouts.save(checkout);
            trace.step(step, id, "ok");
            log.info("Checkout {} confirmed: subtotal {}, discounts {} + {}, tax {}, total {}", id,
                quote.subtotal(), quote.volumeDiscount(), quote.couponDiscount(), quote.tax(), quote.total());
            audit.confirmed(checkout);

            double invoice = invoiceCheck.invoiceTotal(held, request.couponCode());
            if (!invoiceCheck.matches(checkout.getTotal(), invoice)) {
                audit.invoiceMismatch(checkout, invoice);
            }
            return checkout;
        } catch (OutOfStockException | InvalidCheckoutException e) {
            trace.step(step, id, "failed");
            log.warn("Checkout {} stopped at {}: {}", id, step, e.getMessage());
            fail(checkout, held, e instanceof OutOfStockException ? "out of stock" : "invalid basket");
            throw e;
        } catch (InventoryUnavailableException e) {
            trace.step(step, id, "failed");
            log.error("Checkout {} failed at {}: {}", id, step, e.getMessage(), e);
            fail(checkout, held, "inventory unavailable");
            throw e;
        } catch (RuntimeException e) {
            trace.step(step, id, "failed");
            log.warn("Checkout {} failed at {}: {}", id, step, e.toString());
            fail(checkout, held, "internal error");
            throw e;
        }
    }

    public List<Checkout> recent() {
        return checkouts.findTop20ByOrderByIdDesc();
    }

    private void validate(CheckoutRequest request) {
        if (request == null || request.customer() == null || request.customer().isBlank()) {
            throw new InvalidCheckoutException("A checkout needs a customer");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new InvalidCheckoutException("The basket is empty");
        }
        if (request.lines().size() > MAX_LINES) {
            throw new InvalidCheckoutException("A basket holds at most " + MAX_LINES + " lines");
        }
        for (CheckoutRequest.Line line : request.lines()) {
            if (line.itemId() == null || line.quantity() <= 0) {
                throw new InvalidCheckoutException("Every basket line needs an item and at least one unit");
            }
        }
    }

    /** Puts back what was reserved, then records the checkout as failed. */
    private void fail(Checkout checkout, List<PricingEngine.PricedLine> held, String reason) {
        for (PricingEngine.PricedLine line : held) {
            try {
                inventory.release(line.itemId(), line.quantity(), checkout.getId());
            } catch (RuntimeException e) {
                log.error("Checkout {}: could not return {} x '{}' to stock, release them by hand: {}",
                    checkout.getId(), line.quantity(), line.name(), e.getMessage());
            }
        }
        checkout.fail(reason);
        checkouts.save(checkout);
        audit.cancelled(checkout, reason);
    }
}
