package com.example.oop;

public class AuditedOrderService {

    @AuditedAction("create-order")
    public String createOrder(String userId) {
        if (userId == null || userId.isBlank()) {
            return "INVALID";
        }
        return "ORDER_CREATED";
    }

    @AuditedAction("cancel-order")
    public String cancelOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return "INVALID";
        }
        return "ORDER_CANCELED";
    }

    public String health() {
        return "OK";
    }
}
