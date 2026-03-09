package com.example.oop;

public class StandardShippingStrategy implements ShippingStrategy {

    @Override
    public int shippingFee(int itemCount, int subtotal) {
        if (itemCount <= 0) {
            return 0;
        }
        return subtotal >= 5000 ? 0 : 500;
    }
}
