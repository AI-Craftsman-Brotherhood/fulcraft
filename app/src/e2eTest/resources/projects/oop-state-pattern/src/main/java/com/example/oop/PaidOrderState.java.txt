package com.example.oop;

public class PaidOrderState implements OrderState {

    @Override
    public String name() {
        return "PAID";
    }

    @Override
    public void pay(OrderWorkflow workflow) {
        // Already paid.
    }

    @Override
    public void ship(OrderWorkflow workflow) {
        workflow.transitionTo(new ShippedOrderState());
    }
}
