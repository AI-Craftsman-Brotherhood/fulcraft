package com.example.oop;

public final class RateDiscountPolicy implements DiscountPolicy {

    private final int percent;

    public RateDiscountPolicy(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be in range 0..100");
        }
        this.percent = percent;
    }

    @Override
    public int applyTo(int price) {
        int discount = (price * percent) / 100;
        return Math.max(0, price - discount);
    }
}
