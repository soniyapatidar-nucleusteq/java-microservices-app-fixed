package com.example.inventory;
import jakarta.persistence.*;

@Entity
public class Item {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int quantity;
    private double unitPrice;

    public Item() {}
    public Item(Long id, String name, int quantity) { this(id, name, quantity, 0.0); }
    public Item(Long id, String name, int quantity, double unitPrice) {
        this.id = id; this.name = name; this.quantity = quantity; this.unitPrice = unitPrice;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
}
