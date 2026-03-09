package com.example.oop;

public class ExpressShippingStrategy implements ShippingStrategy {

    @Override
    public int shippingFee(int itemCount, int subtotal) {
        if (itemCount <= 0) {
            return 0;
        }
        return 1200 + (itemCount * 80);
    }
}
