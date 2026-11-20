# Log scenarios from real workflows

There are no scenario buttons and no error endpoints. Every log line below comes from one of two
features used the normal way: the **Checkout** card (order-service, which calls inventory-service) and
the **Supplier restock feed** card (inventory-service). Both are on http://localhost:8081.

The features have known defects, left in on purpose for the platform to catch. They are listed at the
end of this file and are not marked in the code.

## How each stream is written

| Stream | Who writes it | Layout | What the platform can do with it |
|---|---|---|---|
| Class loggers (`c.e.order.checkout.CheckoutWorkflow`, `StockService`, ...) | application code | standard, with `(File.java:line) method` | a pattern finds the file and line |
| `order-trace` | `WorkflowTrace`, one line per order or checkout step | standard, tagged `{order/OrderApplication:57}` | no rule reads the tag; the model reads some |
| `order-audit` | `CheckoutAudit`, the finance audit trail | standard, names no code | nothing to map |
| supplier reject report | `SupplierFeedImporter`, plain `System.out` | unstructured: no time, no level | nothing to map |
| packing slips | `PackingSlipPrinter` (order), plain `System.out` | no time, no class; errors prefixed `ERROR` | nothing to map (yet) |
| reconciliation progress | `StockReconciliation` (inventory), plain `System.out` | no time, no class; errors prefixed `ERROR` | nothing to map (yet) |
| low stock | `StockService` and `SupplierFeedImporter` (inventory), plain `System.out` | no time, no class | nothing to map (yet) |

## The scenarios

Open the platform afterwards: Java Microservice project, Observability, **Log Status** tab (allow about 30 s).

### 1. Standard logs resolved through pattern / rule
Place any checkout in the **Checkout** card, e.g. customer `acme-corp`, coupon `WELCOME10`, 2 x Laptop.
```
... INFO ... c.e.order.checkout.CheckoutWorkflow : (CheckoutWorkflow.java:56) run - Checkout 1 started for acme-corp: 1 lines, 2 units
... INFO ... com.example.inventory.StockService  : (StockService.java:37) reserve - Reserved 2 x 'Laptop' for checkout 1, 8 left
```
The source-path rule reads `CheckoutWorkflow.java:56`; the line maps to `CheckoutWorkflow.run`.

### 2. Standard logs resolved through the model
Place or cancel an order directly in the **Order Service** card (the Order and Cancel buttons,
`POST` / `DELETE /api/orders`). Each writes an `order-trace` line:
```
... INFO ... order-trace : {order/OrderApplication:57} place-order order=12 units=1 outcome=ok
... INFO ... order-trace : {order/OrderApplication:71} cancel-order order=12 units=1 outcome=ok
```
`order/OrderApplication:57` has no file extension and is glued to `/`, so neither the rules nor the
name scan see it. The model reads it as a FILE_LINE, which maps to `OrderApplication.create` / `delete`.

Two things about the model this scenario depends on, both measured:
- It reads the tag reliably only for class names it saw in training (`OrderApplication` is one) and,
  even then, the line number moves its confidence (`:57` reads at 0.93, `:62` not at all). That is why
  the checkout's own trace lines (scenario 3) are not read.
- It runs once per Drain template, on the template's first line, and later lines reuse that reading.
  Drain folds every 4-word INFO message from a Spring service into one catch-all template, which the
  checkout trace lines (4 words) fall into and whose reading is empty. Direct-order lines carry a 5th
  field, `units=`, so they start their own template, whose first line the model reads.

### 3. Standard logs, right structure, resolved by neither
Every checkout writes two kinds:

The `order-trace` steps of a checkout:
```
... INFO ... order-trace : {checkout/CheckoutWorkflow:65} step=reserve-stock checkout=1 outcome=ok
```
Same layout and same tag shape as scenario 2, and it names a real class and line, yet nothing resolves
it: no rule reads the tag, and the model does not recognise `CheckoutWorkflow` (it only labels the few
class names it saw in training, such as `OrderApplication`). This is a gap in the model, not in the line.

The `order-audit` trail:
```
... INFO ... order-audit : checkout 1 confirmed for customer acme-corp: 2 units, charged 2332.80 USD
... WARN ... order-audit : checkout 2 invoice check failed: charged 184.65 USD, invoice says 194.37 USD
```
Timestamp, level, thread and logger are all there, but nothing in the line names code. The WARN is a
real symptom of defect B (below), which the platform cannot tie to code. To get it: basket of
3 x Wireless Mouse + 2 x USB-C Hub, no coupon.

### 4. Unstructured logs resolved by neither
Import a supplier feed whose prices use a decimal comma (supplier `acme-wholesale`):
```
name;quantity;unitPrice
Wireless Mouse;20;27.50
Desk Lamp;12;18,90
Laptop Stand;10;31,00
```
The reject report goes to stdout with no time or level:
```
feed acme-wholesale row 3 rejected: Desk Lamp;12;18,90
feed acme-wholesale: 4 rows read, 2 imported, 2 rejected
```

### 5. Incidents caused by code errors
Each is a bug in our own code, reached through normal use. The platform opens an Incident (and a ticket)
and links it (CAUSED_BY) to the method that threw, read from the innermost frame of our code in the
stack trace, not to the code that caught and logged the error.

| Do this | Exception | Incident's root cause |
|---|---|---|
| **Checkout** with coupon `SUMMER25` (any code not in the price list) | `NullPointerException` | `PricingEngine.couponDiscount` (defect A) |
| **Checkout** a basket of 15 units or more, e.g. 10 x Wireless Mouse + 6 x USB-C Hub | `ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3` | `PricingEngine.volumeRate` (defect D) |
| **Supplier restock feed** whose header calls the price column `price`: `name;quantity;price` then `Wireless Mouse;20;27.50` | `ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 3` | `SupplierFeedImporter.importFeed` (defect E) |
| **Customer summary** for a customer with no confirmed checkout (e.g. `initech` after the coupon failure, or any new name) | `ArithmeticException: / by zero` | `CustomerSummary.of` (defect F) |

In each case the first ERROR line is logged by the code that caught the error
(`CheckoutController.failed`, or Spring's own dispatcher), and the trace follows it. The Incident must
point past that line to where the trace says the error was raised.

For contrast, two errors that are not code faults:
- **Infrastructure:** load the dashboard, run `docker compose stop inventory-service`, then place a
  checkout from the open page. `InventoryUnavailableException` / `UnknownHostException`: the Incident's
  cause is the `inventory-service` service, and its trace is where it surfaced. Restart with
  `docker compose start inventory-service`.
- **Handled business outcome:** a checkout for more units than are in stock (e.g. 99 x 27-inch Monitor)
  is a WARN: it resolves through pattern but opens no Incident.

### 6. Print statements (no timestamp, no class or file name)
Several parts of the code print straight to the console with `System.out.println`, for the people
who read it: the warehouse dock (packing slips), the buyers (low stock), the stock team
(reconciliation). The lines carry no timestamp, logger, class or file. Errors are written with an
`ERROR` prefix, which makes them errors (and so Incidents) for the platform; the format is
`partial` (a level word, no time) or `unstructured` (neither).

**Without an error** (no Incident):

| Do this | Lines | Printed by |
|---|---|---|
| Any **Checkout** | `slip 1 for acme-corp: 2 x Laptop, 1 x USB-C Hub` / `slip 1 pick 2 x Laptop from bin A-01` / `slip 1 sent to dock-2` | `PackingSlipPrinter.print` (order-service) |
| **Checkout** that leaves an item under 5 units, e.g. 6 x Laptop | `low stock: Laptop has 2 left (reorder at 5)` | `StockService.lowStock` (inventory-service) |
| **Supplier feed** delivering too few to reach 5, e.g. `Laptop;1;1150.00` | `low stock: Laptop has 3 left (reorder at 5)` (same wording, **another file**) | `SupplierFeedImporter.lowStock` (inventory-service) |
| **Run reconciliation** | `reconcile run 1 pass 2: 0 of 10 items checked, 10 retrying` / `reconcile run 1 cancelled in pass 3, 0 of 10 items checked` | `StockReconciliation` (inventory-service) |

**With an error** (each opens an Incident; do one at a time, a few seconds apart, as error lines
without a time of their own that arrive together are read as one error):

| Do this | Line | Printed by |
|---|---|---|
| **Supplier feed** adding a new product, e.g. `name;quantity;unitPrice` / `Desk Lamp;12;18.90`, then **Checkout** 2 x Desk Lamp | `ERROR slip 5: no bin location for Desk Lamp, slip held` (defect G) | `PackingSlipPrinter.print` (order-service) |
| Then delete Desk Lamp in the Inventory list and click **Reprint slip** on that checkout | `ERROR lookup failed for item 11` | `PackingSlipPrinter.lookupFailed` (order-service) |
| **Run reconciliation** (scenario 7) | `ERROR lookup failed for item 7`, one per item per pass (same wording as the line above, **another service and file**) | `StockReconciliation.lookupFailed` (inventory-service) |

### 7. Database connection exhaustion (RCA and blast radius)
Both services use one Postgres (`postgres` in docker-compose, database `shop`), with a pool of a
single connection each (`spring.datasource.hikari.maximum-pool-size=1`, timeout 5 s).

1. **Stock reconciliation** card: **Run reconciliation**. The run holds inventory-service's only
   connection for its whole run (one read-only transaction, its snapshot) while its 4 worker threads
   each need a connection for their ledger check: they wait 5 s, fail and are retried, pass after
   pass (defect H). The run never finishes.
2. While it runs, everything in inventory-service that touches the database fails after 5 s:
   the Inventory list, **Checkout** (reserve stock; order-service then reports inventory unreachable,
   503, after its retries), **Supplier feed**, **Reprint slip**.
   - inventory-service logs `inventory-pool - Connection is not available, request timed out after 5000ms`
     with traces (category `resource_exhausted`), and the workers print `ERROR lookup failed for item N`.
   - order-service logs `Retries exhausted after 3 attempts calling inventory-service ... Read timed out`.
3. **Cancel reconciliation**: the workers are stopped, the run's transaction is rolled back and the
   connection returned; the next request works at once. The workers still waiting for a connection
   are interrupted, and Hibernate logs `inventory-pool - Interrupted during connection acquisition`
   (ERROR), so cancelling opens one small Incident of its own.

What the platform shows today (its current rules, not the app): the pool Incident's cause is
`service:inventory-service` (resource exhaustion is read as the service's own problem), its
FAILED_IN the frame that timed out; the code graph has one `database:postgresql://postgres:5432/shop`
node that both services depend on, and a `service:postgres` node from docker-compose.

## Known defects (left in on purpose)

| | Where | What is wrong | What shows it |
|---|---|---|---|
| A | `PricingEngine.couponDiscount` | An unknown coupon code is not rejected: `PERCENT_OFF.get(code)` returns null and unboxing it throws. The customer gets a 500 instead of "coupon not accepted". | ERROR + stack trace, Incident (scenario 5) |
| B | `PricingEngine.quote` | The volume discount tier is chosen from the units in the whole basket, but the price list gives it per basket line. 3 + 2 units get 5% off that they should not. | `order-audit` "invoice check failed" WARN (scenario 3) |
| C | `SupplierFeedImporter.importFeed` | Prices are parsed with `Double.parseDouble`, so a decimal comma (`18,90`) is rejected and the delivery never reaches the shelf. | the reject report on stdout (scenario 4) |
| D | `PricingEngine.volumeRate` | The tier index is capped at `VOLUME_TIERS.size()` instead of `size() - 1`, so 15 or more units asks for tier 3 of a 3-tier list. | Incident (scenario 5) |
| E | `SupplierFeedImporter.importFeed` | Columns are found by their header name, and a missing one (`price` instead of `unitPrice`) is not checked: its index is -1. | Incident (scenario 5) |
| F | `CustomerSummary.of` | The average basket divides by the number of confirmed checkouts, which is 0 for a customer who has none. | Incident (scenario 5) |
| G | `SupplierFeedImporter.importFeed` | A product a supplier feed creates gets no warehouse bin, so its packing slip is held at the dock. | `ERROR slip N: no bin location ...` print (scenario 6) |
| H | `StockReconciliation.checkAll` | The run keeps one transaction (and so the pool's only connection) open while it waits for workers that each need a connection; failed checks are retried without limit. | pool exhaustion across inventory and checkout (scenario 7) |
