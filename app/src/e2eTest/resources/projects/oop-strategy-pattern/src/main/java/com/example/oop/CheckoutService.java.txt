package com.example.oop;

public class CheckoutService {

    private ShippingStrategy shippingStrategy;

    public CheckoutService(ShippingStrategy shippingStrategy) {
        this.shippingStrategy = shippingStrategy;
    }

    public void setShippingStrategy(ShippingStrategy shippingStrategy) {
        this.shippingStrategy = shippingStrategy;
    }

    public int totalWithShipping(int itemCount, int subtotal) {
        int shipping = shippingStrategy.shippingFee(itemCount, subtotal);
        return Math.max(0, subtotal + shipping);
    }
}
