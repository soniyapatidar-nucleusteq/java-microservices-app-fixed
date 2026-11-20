package com.example.order.checkout;

import com.example.order.ProductOrder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Packing slips for the warehouse. The dock's label printer reads the service console,
 * so a slip is printed there: what to pick, from which bin, and which dock it ships
 * from. Each item's bin is looked up in inventory-service at print time, as items move.
 *
 * A slip that cannot be completed is held, not half printed: the pickers would
 * otherwise ship an incomplete parcel.
 */
@Component
public class PackingSlipPrinter {

    static final int DOCKS = 3;

    private final InventoryClient inventory;

    public PackingSlipPrinter(InventoryClient inventory) {
        this.inventory = inventory;
    }

    /** Prints the slip for one checkout's orders. Returns whether it was printed. */
    public boolean print(long checkout, String customer, List<ProductOrder> lines) {
        System.out.println("slip " + checkout + " for " + customer + ": " + lines.stream()
            .map(o -> o.getQuantity() + " x " + o.getProductName()).collect(Collectors.joining(", ")));
        for (ProductOrder line : lines) {
            InventoryClient.StockItem item;
            try {
                item = inventory.getItem(line.getItemId());
            } catch (RuntimeException e) {
                lookupFailed(line.getItemId());
                return false;
            }
            if (item.bin() == null || item.bin().isBlank()) {
                System.out.println("ERROR slip " + checkout + ": no bin location for " + item.name() + ", slip held");
                return false;
            }
            System.out.println("slip " + checkout + " pick " + line.getQuantity() + " x " + item.name()
                + " from bin " + item.bin());
        }
        System.out.println("slip " + checkout + " sent to dock-" + (checkout % DOCKS + 1));
        return true;
    }

    private static void lookupFailed(Long itemId) {
        System.out.println("ERROR lookup failed for item " + itemId);
    }
}
