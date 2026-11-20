package com.example.inventory;
import jakarta.persistence.*;

@Entity
public class Item {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int quantity;
    private double unitPrice;
    /** Where the item is shelved in the warehouse, e.g. A-03. */
    private String bin;

    public Item() {}
    public Item(Long id, String name, int quantity) { this(id, name, quantity, 0.0); }
    public Item(Long id, String name, int quantity, double unitPrice) { this(id, name, quantity, unitPrice, null); }
    public Item(Long id, String name, int quantity, double unitPrice, String bin) {
        this.id = id; this.name = name; this.quantity = quantity; this.unitPrice = unitPrice; this.bin = bin;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public String getBin() { return bin; }
    public void setBin(String bin) { this.bin = bin; }
}
