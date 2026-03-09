package com.example.oop;

import java.util.LinkedHashSet;
import java.util.Set;

public class LowStockObserver implements InventoryObserver {

    private final int threshold;
    private final Set<String> lowStockItems = new LinkedHashSet<>();

    public LowStockObserver(int threshold) {
        this.threshold = Math.max(0, threshold);
    }

    @Override
    public void onStockChanged(String itemCode, int previousStock, int currentStock) {
        if (currentStock <= threshold) {
            lowStockItems.add(itemCode);
        } else {
            lowStockItems.remove(itemCode);
        }
    }

    public Set<String> lowStockItems() {
        return Set.copyOf(lowStockItems);
    }
}
