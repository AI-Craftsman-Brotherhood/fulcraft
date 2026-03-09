package com.example.oop;

public class LoyaltyService {

    public int discountedPrice(int price, MembershipLevel level) {
        if (price <= 0 || level == null) {
            return 0;
        }
        int discount = (price * level.discountPercent()) / 100;
        return price - discount;
    }

    public String badgeLabel(MembershipLevel level) {
        if (level == null) {
            return "UNKNOWN";
        }
        return switch (level) {
            case REGULAR -> "Regular";
            case SILVER -> "Silver";
            case GOLD -> "Gold";
        };
    }
}
