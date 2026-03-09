package com.example.oop;

public class ShippedOrderState implements OrderState {

    @Override
    public String name() {
        return "SHIPPED";
    }

    @Override
    public void pay(OrderWorkflow workflow) {
        // Final state.
    }

    @Override
    public void ship(OrderWorkflow workflow) {
        // Final state.
    }
}
