package com.example.oop;

public interface InventoryObserver {

    void onStockChanged(String itemCode, int previousStock, int currentStock);
}
