package com.example.oop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventorySubject {

    private final Map<String, Integer> stockByItem = new HashMap<>();
    private final List<InventoryObserver> observers = new ArrayList<>();

    public void addObserver(InventoryObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    public void removeObserver(InventoryObserver observer) {
        observers.remove(observer);
    }

    public int currentStock(String itemCode) {
        return stockByItem.getOrDefault(itemCode, 0);
    }

    public void setStock(String itemCode, int newStock) {
        if (itemCode == null || itemCode.isBlank()) {
            return;
        }
        int normalized = Math.max(0, newStock);
        int previous = stockByItem.getOrDefault(itemCode, 0);
        if (previous == normalized) {
            return;
        }
        stockByItem.put(itemCode, normalized);
        for (InventoryObserver observer : observers) {
            observer.onStockChanged(itemCode, previous, normalized);
        }
    }
}
