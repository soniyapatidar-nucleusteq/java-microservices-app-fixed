package com.example.inventory;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(Long id) {
        super("Inventory Not Found Exception: no inventory item with id " + id);
    }
}
