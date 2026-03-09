package com.example.oop;

public final class FlatDiscountPolicy implements DiscountPolicy {

    private final int amount;

    public FlatDiscountPolicy(int amount) {
        this.amount = Math.max(0, amount);
    }

    @Override
    public int applyTo(int price) {
        return Math.max(0, price - amount);
    }
}
