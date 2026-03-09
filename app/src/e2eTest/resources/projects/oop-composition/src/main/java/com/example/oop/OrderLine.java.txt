package com.example.oop;

public class OrderLine {

    private final String productName;
    private final int unitPrice;
    private final int quantity;

    public OrderLine(String productName, int unitPrice, int quantity) {
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public String productName() {
        return productName;
    }

    public int lineTotal() {
        if (unitPrice <= 0 || quantity <= 0) {
            return 0;
        }
        return unitPrice * quantity;
    }
}
