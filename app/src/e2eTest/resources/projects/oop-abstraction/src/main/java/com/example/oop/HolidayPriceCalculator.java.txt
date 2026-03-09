package com.example.oop;

public class HolidayPriceCalculator extends AbstractPriceCalculator {

    private final int discountPercent;

    public HolidayPriceCalculator(int discountPercent) {
        if (discountPercent < 0 || discountPercent > 70) {
            throw new IllegalArgumentException("discountPercent must be in range 0..70");
        }
        this.discountPercent = discountPercent;
    }

    @Override
    protected int discountAmount(int basePrice) {
        return (basePrice * discountPercent) / 100;
    }

    @Override
    protected int minimumPrice() {
        return 300;
    }
}
