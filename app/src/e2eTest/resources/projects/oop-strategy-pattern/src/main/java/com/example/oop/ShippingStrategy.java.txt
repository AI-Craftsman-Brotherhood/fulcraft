package com.example.oop;

public interface ShippingStrategy {

    int shippingFee(int itemCount, int subtotal);
}
