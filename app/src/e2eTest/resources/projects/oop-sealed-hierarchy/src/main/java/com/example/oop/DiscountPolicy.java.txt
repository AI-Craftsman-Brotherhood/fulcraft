package com.example.oop;

public sealed interface DiscountPolicy permits FlatDiscountPolicy, RateDiscountPolicy {

    int applyTo(int price);
}
