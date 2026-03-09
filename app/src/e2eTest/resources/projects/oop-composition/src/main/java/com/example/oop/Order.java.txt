package com.example.oop;

import java.util.ArrayList;
import java.util.List;

public class Order {

    private final List<OrderLine> lines = new ArrayList<>();

    public void addLine(OrderLine line) {
        if (line != null) {
            lines.add(line);
        }
    }

    public int lineCount() {
        return lines.size();
    }

    public int subtotal() {
        int total = 0;
        for (OrderLine line : lines) {
            total += line.lineTotal();
        }
        return total;
    }

    public boolean containsProduct(String productName) {
        for (OrderLine line : lines) {
            if (line.productName().equals(productName)) {
                return true;
            }
        }
        return false;
    }
}
