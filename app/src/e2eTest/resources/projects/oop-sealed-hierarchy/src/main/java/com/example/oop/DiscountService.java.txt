package com.example.oop;

public class DiscountService {

    public int calculate(int originalPrice, DiscountPolicy policy) {
        if (originalPrice <= 0 || policy == null) {
            return 0;
        }
        return policy.applyTo(originalPrice);
    }

    public String describe(DiscountPolicy policy) {
        if (policy instanceof FlatDiscountPolicy) {
            return "FLAT";
        }
        if (policy instanceof RateDiscountPolicy) {
            return "RATE";
        }
        return "UNKNOWN";
    }
}
