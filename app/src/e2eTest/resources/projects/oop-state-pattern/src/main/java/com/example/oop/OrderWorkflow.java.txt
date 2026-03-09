package com.example.oop;

public class OrderWorkflow {

    private OrderState state = new DraftOrderState();

    public String currentState() {
        return state.name();
    }

    public void pay() {
        state.pay(this);
    }

    public void ship() {
        state.ship(this);
    }

    void transitionTo(OrderState nextState) {
        if (nextState != null) {
            state = nextState;
        }
    }
}
