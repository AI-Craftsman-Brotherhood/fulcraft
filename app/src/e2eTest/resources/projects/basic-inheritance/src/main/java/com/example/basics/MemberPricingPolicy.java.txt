package com.example.basics;

public class MemberPricingPolicy implements PricingPolicy {

    private final int discountPercent;

    public MemberPricingPolicy(int discountPercent) {
        if (discountPercent < 0 || discountPercent > 50) {
            throw new IllegalArgumentException("discount percent must be in range 0..50");
        }
        this.discountPercent = discountPercent;
    }

    @Override
    public int applyDiscount(int price) {
        if (price <= 0) {
            return 0;
        }
        int discount = (price * discountPercent) / 100;
        return price - discount;
    }
}
