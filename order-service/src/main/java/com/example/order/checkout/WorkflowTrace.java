package com.example.order.checkout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One line per checkout step on the `checkout-trace` stream, so support can follow a
 * checkout from basket to confirmation:
 *
 *   {checkout/CheckoutWorkflow:97} step=reserve-stock checkout=42 outcome=ok
 *
 * The tag in braces is where in the workflow the step was recorded (the caller's
 * package, class and line), taken from the call stack rather than written by hand,
 * so it cannot go stale when the workflow changes.
 */
@Component
public class WorkflowTrace {

    private static final Logger trace = LoggerFactory.getLogger("checkout-trace");
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public void step(String step, long checkout, String outcome) {
        String where = WALKER.walk(frames -> frames.skip(1).findFirst()
            .map(f -> location(f.getDeclaringClass(), f.getLineNumber()))
            .orElse("{unknown}"));
        trace.info("{} step={} checkout={} outcome={}", where, step, checkout, outcome);
    }

    static String location(Class<?> type, int line) {
        String pkg = type.getPackageName();
        return "{" + pkg.substring(pkg.lastIndexOf('.') + 1) + "/" + type.getSimpleName() + ":" + line + "}";
    }
}
