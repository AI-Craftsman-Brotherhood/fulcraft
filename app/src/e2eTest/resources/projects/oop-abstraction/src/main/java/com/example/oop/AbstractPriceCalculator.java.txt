package com.example.oop;

public abstract class AbstractPriceCalculator {

    public final int calculateFinalPrice(int basePrice) {
        if (basePrice <= 0) {
            return 0;
        }
        int discounted = basePrice - discountAmount(basePrice);
        return Math.max(discounted, minimumPrice());
    }

    protected abstract int discountAmount(int basePrice);

    protected int minimumPrice() {
        return 100;
    }
}
