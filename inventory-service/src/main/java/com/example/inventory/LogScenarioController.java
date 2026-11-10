package com.example.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits log lines that exercise each outcome of the observability platform's log
 * resolution (rules, then Drain and the model, then mapping to a node of the code graph):
 *
 *   noise   - junk, non-standard and unrelated lines. Nothing in them names this code,
 *             so they must stay UNRESOLVED (mapped to the service, not to code).
 *   pattern - lines a log rule (pattern) recognises; what the rule or the line names
 *             maps to a class or method, so they are RESOLVED THROUGH PATTERN.
 *   model   - lines no rule recognises; the model reads them and the file and function
 *             it finds map to a method, so they are RESOLVED THROUGH MODEL.
 *
 * The lines go through the "log-scenarios" logger on purpose. A class logger
 * (c.e.inventory.InventoryApplication) is itself a reference to code, so any line it
 * wrote would resolve whatever it said; this logger name names no code. Each line is
 * deliberately worded and sized: the model runs once per new Drain template, so lines
 * of one level are kept different enough in length and words not to share a template.
 */
@RestController
@RequestMapping("/api/log-scenarios")
@CrossOrigin(origins = "*")
public class LogScenarioController {

    private static final Logger scenarios = LoggerFactory.getLogger("log-scenarios");

    private final InventoryApplication inventory;

    public LogScenarioController(InventoryApplication inventory) {
        this.inventory = inventory;
    }

    @PostMapping("/{scenario}")
    public Map<String, Object> emit(@PathVariable String scenario) {
        List<Map<String, String>> emitted = new ArrayList<>();
        switch (scenario) {
            case "noise" -> noise(emitted);
            case "pattern" -> pattern(emitted);
            case "model" -> model(emitted);
            case "all" -> { noise(emitted); pattern(emitted); model(emitted); }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown scenario '" + scenario + "': use noise, pattern, model or all");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", scenario);
        body.put("lines", emitted.size());
        body.put("emitted", emitted);
        return body;
    }

    /** Should NOT resolve: no rule matches, and nothing in them is in the code graph. */
    private void noise(List<Map<String, String>> out) {
        info(out, "~~~~ #### !!!! @@@@ ~~~~ #### !!!! @@@@");
        info(out, "the quick brown fox jumps over the lazy dog while the cat sleeps on the warm windowsill");
        info(out, "weather report: sunny skies, 31 degrees, light wind from the east, rain expected by friday evening");
        info(out, "lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore");
        info(out, "%%%% 42 &&&& 17 #### ??? ++++ 99 ==== 3");
        info(out, "cafeteria menu today: pasta, green salad, garlic bread, mango juice and a chocolate brownie");
        // Non-standard: straight to stdout, with no timestamp, level or logger at all.
        raw(out, "<<<<< garbage frame 0x1f >>>>> ----- static noise ----- <<<<<");
        raw(out, "beep boop beep -- heartbeat of nothing -- blip blop blip");
        raw(out, "{\"msg\": \"hello there\", \"mood\": \"cheerful\", \"color\": \"teal\", \"count\": 7}");
    }

    /** Resolved through a pattern: a log rule matches, and what it names maps to code. */
    private void pattern(List<Map<String, String>> out) {
        // Rule java-npe. A real NullPointerException thrown by InventoryApplication.create: given no
        // item, its own error handler reads item.getName(). Logged as one event (the trace's frames
        // are indented continuation lines), and the innermost own frame maps it to that method.
        try {
            inventory.create(null);
        } catch (NullPointerException e) {
            scenarios.error(trace(e, 6));
            out.add(line("ERROR", "log-scenarios", e + " (+ stack trace)"));
        }

        // Rule java-class-missing. A real ClassNotFoundException: a loader that cannot see the
        // application's classes. The rule's CLASS group maps to the class.
        try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
            isolated.loadClass("com.example.inventory.ItemRepository");
        } catch (ClassNotFoundException e) {
            error(out, "Plugin loader could not load a handler: " + e);
        } catch (java.io.IOException ignored) {
            // closing an empty loader cannot fail
        }

        // Rule ms-retry-exhausted. The rule only categorises it; the dotted name maps it to the method.
        error(out, "Retries exhausted after 3 attempts calling com.example.order.OrderApplication.create for order 17");
    }

    /** Resolved through the model: no rule matches; the model finds the file and function. */
    private void model(List<Map<String, String>> out) {
        warn(out, "order sync paused while reconciling the price of order 7 against stock, "
                + "File \"OrderApplication.java\", line 50, in create");
        warn(out, "order cleanup paused while reconciling order 7, File \"OrderApplication.java\", line 53, in delete");
    }

    private void info(List<Map<String, String>> out, String message) {
        scenarios.info(message);
        out.add(line("INFO", "log-scenarios", message));
    }

    private void warn(List<Map<String, String>> out, String message) {
        scenarios.warn(message);
        out.add(line("WARN", "log-scenarios", message));
    }

    private void error(List<Map<String, String>> out, String message) {
        scenarios.error(message);
        out.add(line("ERROR", "log-scenarios", message));
    }

    private void raw(List<Map<String, String>> out, String message) {
        System.out.println(message);
        out.add(line("(none)", "(stdout)", message));
    }

    /** An exception and its first frames, as the JVM prints them, in one message. */
    private static String trace(Throwable e, int frames) {
        StringBuilder s = new StringBuilder(e.toString());
        StackTraceElement[] stack = e.getStackTrace();
        for (int i = 0; i < Math.min(frames, stack.length); i++) {
            s.append("\n\tat ").append(stack[i]);
        }
        if (stack.length > frames) {
            s.append("\n\t... ").append(stack.length - frames).append(" more");
        }
        return s.toString();
    }

    private static Map<String, String> line(String level, String logger, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("logger", logger);
        m.put("message", message);
        return m;
    }
}
