package com.example.oop;

import java.util.ArrayList;
import java.util.List;

public class Invoice {

    private final List<InvoiceLine> lines = new ArrayList<>();

    public void addLine(InvoiceLine line) {
        if (line != null) {
            lines.add(line);
        }
    }

    public int lineCount() {
        return lines.size();
    }

    public int totalAmount() {
        int total = 0;
        for (InvoiceLine line : lines) {
            total += line.subtotal();
        }
        return total;
    }
}
