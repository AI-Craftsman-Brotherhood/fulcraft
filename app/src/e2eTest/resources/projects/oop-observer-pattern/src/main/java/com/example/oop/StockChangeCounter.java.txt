package com.example.oop;

public class StockChangeCounter implements InventoryObserver {

    private int updateCount;

    @Override
    public void onStockChanged(String itemCode, int previousStock, int currentStock) {
        updateCount++;
    }

    public int updateCount() {
        return updateCount;
    }
}
