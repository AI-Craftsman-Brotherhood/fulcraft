package com.example.basics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryService {

    public Map<String, Integer> countByCategory(List<String> categories) {
        Map<String, Integer> counts = new HashMap<>();
        if (categories == null) {
            return counts;
        }
        for (String category : categories) {
            if (category == null || category.isBlank()) {
                continue;
            }
            counts.put(category, counts.getOrDefault(category, 0) + 1);
        }
        return counts;
    }

    public boolean hasLowStock(Map<String, Integer> stockByItem, int threshold) {
        if (stockByItem == null) {
            return false;
        }
        for (Integer stock : stockByItem.values()) {
            if (stock != null && stock < threshold) {
                return true;
            }
        }
        return false;
    }

    public int totalStock(Map<String, Integer> stockByItem) {
        if (stockByItem == null) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : stockByItem.entrySet()) {
            Integer value = entry.getValue();
            if (value != null) {
                total += value;
            }
        }
        return total;
    }
}
