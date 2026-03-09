package com.example.oop;

public enum MembershipLevel {
    REGULAR {
        @Override
        public int discountPercent() {
            return 0;
        }
    },
    SILVER {
        @Override
        public int discountPercent() {
            return 5;
        }
    },
    GOLD {
        @Override
        public int discountPercent() {
            return 10;
        }
    };

    public abstract int discountPercent();
}
