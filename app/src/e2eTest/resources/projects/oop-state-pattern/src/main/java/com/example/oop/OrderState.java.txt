package com.example.oop;

public interface OrderState {

    String name();

    void pay(OrderWorkflow workflow);

    void ship(OrderWorkflow workflow);
}
