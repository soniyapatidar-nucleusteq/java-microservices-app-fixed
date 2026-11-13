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

## The scenarios

Open the platform afterwards: Java Microservice project, Observability, **Log Status** tab (allow about 30 s).

### 1. Standard logs resolved through pattern / rule
Place any checkout in the **Checkout** card, e.g. customer `acme-corp`, coupon `WELCOME10`, 2 x Laptop.
```
... INFO ... c.e.order.checkout.CheckoutWorkflow : (CheckoutWorkflow.java:53) run - Checkout 1 started for acme-corp: 1 lines, 2 units
... INFO ... com.example.inventory.StockService  : (StockService.java:37) reserve - Reserved 2 x 'Laptop' for checkout 1, 8 left
```
The source-path rule reads `CheckoutWorkflow.java:53`; the line maps to `CheckoutWorkflow.run`.

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
... INFO ... order-trace : {checkout/CheckoutWorkflow:62} step=reserve-stock checkout=1 outcome=ok
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

## Known defects (left in on purpose)

| | Where | What is wrong | What shows it |
|---|---|---|---|
| A | `PricingEngine.couponDiscount` | An unknown coupon code is not rejected: `PERCENT_OFF.get(code)` returns null and unboxing it throws. The customer gets a 500 instead of "coupon not accepted". | ERROR + stack trace, Incident (scenario 5) |
| B | `PricingEngine.quote` | The volume discount tier is chosen from the units in the whole basket, but the price list gives it per basket line. 3 + 2 units get 5% off that they should not. | `order-audit` "invoice check failed" WARN (scenario 3) |
| C | `SupplierFeedImporter.importFeed` | Prices are parsed with `Double.parseDouble`, so a decimal comma (`18,90`) is rejected and the delivery never reaches the shelf. | the reject report on stdout (scenario 4) |
| D | `PricingEngine.volumeRate` | The tier index is capped at `VOLUME_TIERS.size()` instead of `size() - 1`, so 15 or more units asks for tier 3 of a 3-tier list. | Incident (scenario 5) |
| E | `SupplierFeedImporter.importFeed` | Columns are found by their header name, and a missing one (`price` instead of `unitPrice`) is not checked: its index is -1. | Incident (scenario 5) |
| F | `CustomerSummary.of` | The average basket divides by the number of confirmed checkouts, which is 0 for a customer who has none. | Incident (scenario 5) |
