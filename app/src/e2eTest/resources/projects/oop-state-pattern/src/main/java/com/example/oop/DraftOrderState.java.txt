package com.example.oop;

public class DraftOrderState implements OrderState {

    @Override
    public String name() {
        return "DRAFT";
    }

    @Override
    public void pay(OrderWorkflow workflow) {
        workflow.transitionTo(new PaidOrderState());
    }

    @Override
    public void ship(OrderWorkflow workflow) {
        // Shipping is not allowed before payment.
    }
}
