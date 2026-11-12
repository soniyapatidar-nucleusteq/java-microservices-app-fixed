package com.example.order.checkout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One line per order step on the `order-trace` stream, so support can follow an order
 * from basket to confirmation, whichever way it was placed:
 *
 *   {checkout/CheckoutWorkflow:62} step=reserve-stock checkout=42 outcome=ok
 *   {order/OrderApplication:57} place-order order=12 outcome=ok
 *
 * The tag in braces is where the step was recorded (the caller's package, class and
 * line), taken from the call stack rather than written by hand, so it cannot go stale
 * when the code changes.
 */
@Component
public class WorkflowTrace {

    private static final Logger trace = LoggerFactory.getLogger("order-trace");
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** A step of a checkout (CheckoutWorkflow). */
    public void step(String step, long checkout, String outcome) {
        trace.info("{} step={} checkout={} outcome={}", caller(), step, checkout, outcome);
    }

    /** A step of a single order placed or cancelled directly (POST / DELETE /api/orders). */
    public void order(String step, long order, String outcome) {
        trace.info("{} {} order={} outcome={}", caller(), step, order, outcome);
    }

    /** Where the public method was called from: skips this method and the public one. */
    private static String caller() {
        return WALKER.walk(frames -> frames.skip(2).findFirst()
            .map(f -> location(f.getDeclaringClass(), f.getLineNumber()))
            .orElse("{unknown}"));
    }

    static String location(Class<?> type, int line) {
        String pkg = type.getPackageName();
        return "{" + pkg.substring(pkg.lastIndexOf('.') + 1) + "/" + type.getSimpleName() + ":" + line + "}";
    }
}
