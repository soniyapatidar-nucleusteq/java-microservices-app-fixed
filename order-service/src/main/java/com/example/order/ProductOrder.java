package com.example.order;
import jakarta.persistence.*;

@Entity
public class ProductOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String productName;
    private double price;

    public ProductOrder() {}
    public ProductOrder(Long id, String productName, double price) { this.id = id; this.productName = productName; this.price = price; }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
