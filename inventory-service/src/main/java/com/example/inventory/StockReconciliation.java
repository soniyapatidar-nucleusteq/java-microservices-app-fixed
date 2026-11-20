package com.example.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stock reconciliation: every item's shelf count is checked against its stock ledger
 * (the sum of its StockMovements). A difference means stock changed without a movement,
 * e.g. a quantity edited by hand.
 *
 * A run reads the items once, in one read-only transaction, so every check compares
 * against the same snapshot, and checks the items in parallel on a pool of workers.
 * An item whose check fails is checked again in the next pass, until every item has
 * been checked. A run can be cancelled; one run at a time.
 *
 * Progress goes to the console, where the stock team watches it.
 */
@Service
public class StockReconciliation {

    private static final Logger log = LoggerFactory.getLogger(StockReconciliation.class);
    static final int WORKERS = 4;

    private final ItemRepository items;
    private final StockMovementRepository ledger;
    private final TransactionTemplate snapshot;
    private final AtomicInteger runs = new AtomicInteger();
    private volatile Run current;

    public StockReconciliation(ItemRepository items, StockMovementRepository ledger, PlatformTransactionManager tx) {
        this.items = items;
        this.ledger = ledger;
        this.snapshot = new TransactionTemplate(tx);
        this.snapshot.setReadOnly(true);
    }

    public record Status(int run, String state, int pass, int items, int checked, int mismatched, int retrying,
                         Instant startedAt) {}

    /** One run, as the dashboard sees it. */
    static final class Run {
        final int id;
        final Instant startedAt = Instant.now();
        final ExecutorService workers;
        volatile String state = "running";
        volatile boolean cancelled;
        volatile int pass, total, checked, mismatched, retrying;
        Thread coordinator;

        Run(int id) {
            this.id = id;
            AtomicInteger n = new AtomicInteger();
            this.workers = Executors.newFixedThreadPool(WORKERS, r -> new Thread(r, "reconcile-" + id + "-worker-" + n.incrementAndGet()));
        }

        boolean running() { return "running".equals(state); }
    }

    public static class AlreadyRunningException extends RuntimeException {
        AlreadyRunningException(int run) { super("Reconciliation run " + run + " is still running"); }
    }

    public static class NotRunningException extends RuntimeException {
        NotRunningException() { super("No reconciliation is running"); }
    }

    public synchronized Status start() {
        if (current != null && current.running()) {
            throw new AlreadyRunningException(current.id);
        }
        Run run = new Run(runs.incrementAndGet());
        run.coordinator = new Thread(() -> coordinate(run), "reconcile-" + run.id);
        current = run;
        run.coordinator.start();
        log.info("Stock reconciliation run {} started with {} workers", run.id, WORKERS);
        return status(run);
    }

    public synchronized Status cancel() {
        Run run = current;
        if (run == null || !run.running()) {
            throw new NotRunningException();
        }
        run.cancelled = true;
        run.workers.shutdownNow();
        run.coordinator.interrupt();
        log.info("Stock reconciliation run {} cancelled in pass {}", run.id, run.pass);
        return status(run);
    }

    public Status status() {
        return status(current);
    }

    private static Status status(Run run) {
        return run == null ? new Status(0, "idle", 0, 0, 0, 0, 0, null)
            : new Status(run.id, run.state, run.pass, run.total, run.checked, run.mismatched, run.retrying, run.startedAt);
    }

    private void coordinate(Run run) {
        try {
            snapshot.executeWithoutResult(tx -> checkAll(run));
            run.state = "finished";
            System.out.println("reconcile run " + run.id + " finished: " + run.checked + " items checked, "
                + run.mismatched + " off");
        } catch (RuntimeException e) {
            if (run.cancelled) {
                run.state = "cancelled";
                System.out.println("reconcile run " + run.id + " cancelled in pass " + run.pass + ", "
                    + run.checked + " of " + run.total + " items checked");
            } else {
                run.state = "failed";
                log.error("Stock reconciliation run {} failed", run.id, e);
            }
        } finally {
            run.workers.shutdownNow();
        }
    }

    private void checkAll(Run run) {
        List<Item> pending = items.findAll();
        run.total = pending.size();
        while (!pending.isEmpty()) {
            run.pass++;
            Map<Item, Future<Long>> checks = new LinkedHashMap<>();
            for (Item item : pending) {
                checks.put(item, run.workers.submit(() -> ledger.ledgerStock(item.getId())));
            }
            List<Item> retry = new ArrayList<>();
            Throwable firstFailure = null;
            for (Map.Entry<Item, Future<Long>> check : checks.entrySet()) {
                Item item = check.getKey();
                try {
                    long expected = check.getValue().get();
                    run.checked++;
                    if (expected != item.getQuantity()) {
                        run.mismatched++;
                        System.out.println("reconcile run " + run.id + ": " + item.getName() + " shelf "
                            + item.getQuantity() + ", ledger " + expected + ", off by " + (item.getQuantity() - expected));
                    }
                } catch (ExecutionException e) {
                    retry.add(item);
                    if (firstFailure == null) {
                        firstFailure = e.getCause();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("reconciliation interrupted", e);
                }
            }
            if (firstFailure != null) {
                log.error("Reconciliation run {} pass {}: ledger check failed for {} of {} items, retrying them",
                    run.id, run.pass, retry.size(), checks.size(), firstFailure);
            }
            pending = retry;
            run.retrying = retry.size();
            System.out.println("reconcile run " + run.id + " pass " + run.pass + ": " + run.checked + " of "
                + run.total + " items checked, " + retry.size() + " retrying");
        }
    }
}
