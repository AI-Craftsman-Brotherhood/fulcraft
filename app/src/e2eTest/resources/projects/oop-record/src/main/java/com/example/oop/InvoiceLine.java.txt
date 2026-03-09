package com.example.oop;

public record InvoiceLine(String itemName, int unitPrice, int quantity) {

    public InvoiceLine {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName must not be blank");
        }
        if (unitPrice < 0 || quantity < 0) {
            throw new IllegalArgumentException("unitPrice and quantity must be non-negative");
        }
    }

    public int subtotal() {
        return unitPrice * quantity;
    }
}
