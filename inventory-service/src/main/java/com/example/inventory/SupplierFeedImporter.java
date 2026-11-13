package com.example.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a supplier's restock feed: one row per product, `name;quantity;unitPrice`.
 * A header row, when there is one, says the order of the columns, so suppliers may send
 * them in their own order. A product already on the shelf gets the delivered units
 * added and the supplier's new price; an unknown one is added as a new item.
 *
 * Rows that cannot be applied are written to the reject report, a plain text stream
 * (stdout, next to the service log) in the format the suppliers' account managers
 * already read: they settle disputed deliveries from it.
 */
@Service
public class SupplierFeedImporter {

    private static final Logger log = LoggerFactory.getLogger(SupplierFeedImporter.class);
    private static final String SEPARATOR = ";";
    /** The columns of a feed sent without a header row. */
    private static final String[] DEFAULT_COLUMNS = {"name", "quantity", "unitPrice"};

    private final ItemRepository repository;
    private final PrintStream rejectReport;

    @Autowired
    public SupplierFeedImporter(ItemRepository repository) {
        this(repository, System.out);
    }

    SupplierFeedImporter(ItemRepository repository, PrintStream rejectReport) {
        this.repository = repository;
        this.rejectReport = rejectReport;
    }

    public record FeedResult(String supplier, int rows, int imported, int created, List<String> rejected) {}

    @Transactional
    public FeedResult importFeed(String supplier, String feed) {
        String source = supplier == null || supplier.isBlank() ? "unknown-supplier" : supplier.trim();
        List<String> rejected = new ArrayList<>();
        int rows = 0, imported = 0, created = 0;
        String[] lines = feed == null ? new String[0] : feed.split("\\R");
        String[] columns = DEFAULT_COLUMNS;
        int first = 0;
        if (lines.length > 0 && lines[0].trim().toLowerCase().startsWith("name" + SEPARATOR)) {
            columns = lines[0].trim().split(SEPARATOR, -1);
            first = 1;
        }
        int nameAt = columnOf(columns, "name");
        int unitsAt = columnOf(columns, "quantity");
        int priceAt = columnOf(columns, "unitPrice");
        for (int i = first; i < lines.length; i++) {
            String row = lines[i].trim();
            if (row.isEmpty()) {
                continue;
            }
            rows++;
            String[] cells = row.split(SEPARATOR, -1);
            if (cells.length != columns.length || cells[nameAt].isBlank()) {
                reject(source, i + 1, row, rejected);
                continue;
            }
            int units;
            double price;
            try {
                units = Integer.parseInt(cells[unitsAt].trim());
                price = Double.parseDouble(cells[priceAt].trim());
            } catch (NumberFormatException e) {
                reject(source, i + 1, row, rejected);
                continue;
            }
            if (units < 0 || price < 0) {
                reject(source, i + 1, row, rejected);
                continue;
            }
            String name = cells[nameAt].trim();
            Item item = repository.findFirstByNameIgnoreCase(name).orElse(null);
            if (item == null) {
                item = new Item(null, name, 0, price);
                created++;
            }
            item.setQuantity(item.getQuantity() + units);
            item.setUnitPrice(price);
            repository.save(item);
            imported++;
        }
        if (!rejected.isEmpty()) {
            rejectReport.println("feed " + source + ": " + rows + " rows read, " + imported + " imported, "
                + rejected.size() + " rejected");
        }
        log.info("Supplier feed from {} applied: {} of {} rows imported ({} new items), {} rejected",
            source, imported, rows, created, rejected.size());
        return new FeedResult(source, rows, imported, created, rejected);
    }

    /** Where a column is in the feed's header. */
    private static int columnOf(String[] columns, String column) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].trim().equalsIgnoreCase(column)) {
                return i;
            }
        }
        return -1;
    }

    private void reject(String supplier, int rowNumber, String row, List<String> rejected) {
        rejectReport.println("feed " + supplier + " row " + rowNumber + " rejected: " + row);
        rejected.add(row);
    }
}
